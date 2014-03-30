/*
 * MediathekView
 * Copyright (C) 2008 W. Xaver
 * W.Xaver[at]googlemail.com
 * http://zdfmediathk.sourceforge.net/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package mServer.upload;

import java.util.Iterator;
import mServer.daten.MSVDatenUpload;
import mServer.daten.MSVSearchTask;
import mServer.tool.MSVDaten;
import mServer.tool.MSVKonstanten;
import mServer.tool.MSVLog;
import mServer.tool.MSVWarten;
import msearch.daten.MSConfig;

public class MSVUpload {

    // Konstanten Upload
    public static final String UPLOAD_ART_FTP = "ftp";
    public static final String UPLOAD_ART_COPY = "copy";
    public static final String FORMAT_JSON = "json";
    public static final String FORMAT_XML = "xml";
    public static final String LISTE_DIFF = "diff";
    public static final String LISTE_ORG = "org";

    public static void upload(MSVSearchTask aktSearchTask) {
        // ==================================================
        // erst lokale Exports erledigen
        if (!MSVDaten.system[MSVKonstanten.SYSTEM_EXPORT_FILE_FILMLISTE_NR].isEmpty()) {
            MSVCopy.copy(MSConfig.getPathFilmlist_json_xz(), MSVDaten.system[MSVKonstanten.SYSTEM_EXPORT_FILE_FILMLISTE_NR]);
        }
        if (aktSearchTask.orgListeAnlegen() && !MSVDaten.system[MSVKonstanten.SYSTEM_EXPORT_FILE_FILMLISTE_ORG_NR].isEmpty()) {
            MSVCopy.copy(MSConfig.getPathFilmlist_org_xz(), MSVDaten.system[MSVKonstanten.SYSTEM_EXPORT_FILE_FILMLISTE_ORG_NR]);
        }
        if (!MSVDaten.system[MSVKonstanten.SYSTEM_EXPORT_FILE_FILMLISTE_DIFF_NR].isEmpty()) {
            MSVCopy.copy(MSConfig.getPathFilmlist_diff_xz(), MSVDaten.system[MSVKonstanten.SYSTEM_EXPORT_FILE_FILMLISTE_DIFF_NR]);
        }

        // ======================================================
        // jetzt die anderen Uploads erledigen
        String destFileName;
        String srcPathFile;
        Iterator<MSVDatenUpload> it = MSVDaten.listeUpload.iterator();
        if (MSVDaten.listeUpload.size() > 0) {
            // nach dem Suchen Rechner Zeit zum Abau aller Verbindungen geben
            new MSVWarten().sekundenWarten(30);
        }
        while (it.hasNext()) {
            MSVDatenUpload datenUpload = it.next();
            srcPathFile = datenUpload.getFilmlisteSrc();
            destFileName = aktSearchTask.getExportNameFilmliste(datenUpload);
            switch (datenUpload.arr[MSVDatenUpload.UPLOAD_LISTE_NR]) {
                case (MSVUpload.LISTE_DIFF):
                    MSVLog.systemMeldung("--------------------------");
                    MSVLog.systemMeldung("Upload Diff-Liste");
                    break;
                case (MSVUpload.LISTE_ORG):
                    MSVLog.systemMeldung("--------------------------");
                    MSVLog.systemMeldung("Upload Org-Liste");
                    break;
                default:
                    MSVLog.systemMeldung("--------------------------");
                    MSVLog.systemMeldung("Upload Filmliste");
            }

            switch (datenUpload.arr[MSVDatenUpload.UPLOAD_ART_NR]) {
                case UPLOAD_ART_COPY:
                    // ==============================================================
                    // kopieren
                    if (!uploadCopy_(srcPathFile, destFileName, datenUpload)) {
                        // wenns nicht geklappt hat nochmal versuchen
                        new MSVWarten().sekundenWarten(60);
                        MSVLog.systemMeldung("2. Versuch Upload copy");
                        if (!uploadCopy_(srcPathFile, destFileName, datenUpload)) {
                            MSVLog.fehlerMeldung(798956236, MSVUpload.class.getName(), "Copy, 2.Versuch nicht geklappt");
                        }
                    }
                    break;

                case UPLOAD_ART_FTP:
                    // ==============================================================
                    // ftp
                    if (!uploadFtp_(srcPathFile, destFileName, datenUpload)) {
                        // wenns nicht geklappt hat nochmal versuchen
                        new MSVWarten().sekundenWarten(60);
                        MSVLog.systemMeldung("2. Versuch Upload FTP");
                        if (!uploadFtp_(srcPathFile, destFileName, datenUpload)) {
                            MSVLog.fehlerMeldung(649896079, MSVUpload.class.getName(), "FTP, 2.Versuch nicht geklappe");
                        }
                    }
                    break;
            }
        }
        MSVLog.systemMeldung("Upload Ok");
    }

    private static boolean uploadFtp_(String srcPathFile, String destFileName, MSVDatenUpload datenUpload) {
        boolean ret = false;
        if (MSVUploadFtp.uploadFtp(srcPathFile, destFileName, datenUpload)) {
            if (MSVMelden.melden(destFileName, datenUpload)) {
                ret = true;
            }
        }
        return ret;
    }

    private static boolean uploadCopy_(String srcPathFile, String destFileName, MSVDatenUpload datenUpload) {
        boolean ret = false;
        if (MSVUploadCopy.copy(srcPathFile, destFileName, datenUpload)) {
            if (MSVMelden.melden(destFileName, datenUpload)) {
                ret = true;
            }
        }
        return ret;

    }
}