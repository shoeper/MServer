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
package mvServer.search;

import java.util.ArrayList;
import msearch.MSearch;
import msearch.tool.MSConfig;
import msearch.tool.MSLog;
import mvServer.daten.MvSSearchTask;
import mvServer.tool.MvSDaten;
import mvServer.tool.MvSKonstanten;
import mvServer.tool.MvSLog;

public class MvSSearch {

    MSearch mSearch;

    public MvSSearch() {
        this.mSearch = null;
        MSConfig.dirFilme = MvSDaten.getVerzeichnisFilme();
    }

    public boolean filmeSuchen(MvSSearchTask aktSearchTask) {
        boolean ret = true;
        try {
            // ===========================================
            // den nächsten Suchlauf starten
            MvSLog.systemMeldung("");
            MvSLog.systemMeldung("-----------------------------------");
            MvSLog.systemMeldung("Filmsuche starten");
            mSearch = new MSearch();
            // was und wie
            MSConfig.senderAllesLaden = aktSearchTask.allesLaden();
            MSConfig.updateFilmliste = aktSearchTask.updateFilmliste();
            MSConfig.nurSenderLaden = arrLesen(aktSearchTask.arr[MvSSearchTask.SUCHEN_SENDER_NR].trim());
            MSConfig.orgFilmlisteErstellen = aktSearchTask.orgListeAnlegen();
            MSConfig.orgFilmliste = MvSDaten.system[MvSKonstanten.SYSTEM_FILMLISTE_ORG_NR];
            // und noch evtl. ein paar Imports von Filmlisten anderer Server
            MSConfig.importUrl__anhaengen = MvSDaten.system[MvSKonstanten.SYSTEM_IMPORT_URL_EXTEND_NR];
            MSConfig.importUrl__ersetzen = MvSDaten.system[MvSKonstanten.SYSTEM_IMPORT_URL_REPLACE_NR];
            // Rest
            MSConfig.setUserAgent(MvSDaten.getUserAgent());
            MSConfig.proxyUrl = MvSDaten.system[MvSKonstanten.SYSTEM_PROXY_URL_NR];
            MSConfig.proxyPort = MvSDaten.getProxyPort();
            MSConfig.debug = MvSDaten.debug;
            MSLog.setLogfile(MvSDaten.getLogDatei_msearch());

            Thread t = new Thread(mSearch);
            t.start();
            MvSLog.systemMeldung("Filme suchen gestartet");
            // ===========================================
            // warten auf das Ende
            int warten = aktSearchTask.allesLaden() == true ? MvSKonstanten.WARTEZEIT_ALLES_LADEN : MvSKonstanten.WARTEZEIT_UPDATE_LADEN;
            t.join(warten);
            // ===========================================
            // erst mal schauen ob noch was läuft
            if (t != null) {
                if (t.isAlive()) {
                    MvSLog.fehlerMeldung(915147623, MvSSearch.class.getName(), "Der letzte Suchlauf läuft noch");
                    if (mSearch != null) {
                        MvSLog.systemMeldung("");
                        MvSLog.systemMeldung("");
                        MvSLog.systemMeldung("================================");
                        MvSLog.systemMeldung("================================");
                        MvSLog.systemMeldung("und wird jetzt gestoppt");
                        MvSLog.systemMeldung("================================");
                        MvSLog.systemMeldung("================================");
                        MvSLog.systemMeldung("");
                        //und jetzt STOPPEN!!!!!!!!
                        //MSConfig.setStop();
                        mSearch.stop();
                    }
                    t.join(5 * 60 * 1000); // 5 Minuten warten, das Erstellen/Komprimieren der Liste dauert
                    if (t.isAlive()) {
                        MvSLog.systemMeldung("");
                        MvSLog.systemMeldung("");
                        MvSLog.systemMeldung("================================");
                        MvSLog.systemMeldung("================================");
                        MvSLog.systemMeldung("und noch gekillt");
                        MvSLog.systemMeldung("================================");
                        MvSLog.systemMeldung("================================");
                        MvSLog.systemMeldung("");
                        ret = false;
                    }
                    //jetzt ist Schicht im Schacht
                    t.stop();
                }
            }
        } catch (Exception ex) {
            MvSLog.fehlerMeldung(636987308, MvSSearch.class.getName(), "filmeSuchen", ex);
        }
        MvSLog.systemMeldung("filmeSuchen beendet");
        mSearch = null;
        return ret;
    }

    private String[] arrLesen(String s) {
        ArrayList<String> arr = new ArrayList<>();
        String tmp = "";
        s = s.trim();
        if (s.equals("")) {
            return null;
        }
        for (int i = 0; i < s.length(); ++i) {
            if (s.charAt(i) == ',') {
                if (!tmp.equals("")) {
                    arr.add(tmp);
                }
                tmp = "";
            } else {
                tmp += s.charAt(i);
            }
        }
        if (!tmp.equals("")) {
            arr.add(tmp);
        }
        return arr.toArray(new String[]{});
    }
}
