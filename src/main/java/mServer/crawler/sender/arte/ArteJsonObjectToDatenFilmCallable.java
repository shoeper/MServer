package mServer.crawler.sender.arte;

import java.io.IOException;
import java.time.LocalTime;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import de.mediathekview.mlib.daten.DatenFilm;
import de.mediathekview.mlib.tool.MVHttpClient;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import mServer.crawler.CrawlerTool;
import mServer.crawler.sender.newsearch.Qualities;
import mServer.tool.DateWithoutTimeComparer;
import mServer.tool.MserverDatumZeit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ArteJsonObjectToDatenFilmCallable implements Callable<DatenFilm>
{
    private static final Logger LOG = LogManager.getLogger(ArteJsonObjectToDatenFilmCallable.class);
    private static final String JSON_ELEMENT_KEY_CATEGORY = "category";
    private static final String JSON_ELEMENT_KEY_SUBCATEGORY = "subcategory";
    private static final String JSON_ELEMENT_KEY_NAME = "name";
    private static final String JSON_ELEMENT_KEY_TITLE = "title";
    private static final String JSON_ELEMENT_KEY_SUBTITLE = "subtitle";
    private static final String JSON_ELEMENT_KEY_URL = "url";
    private static final String JSON_ELEMENT_KEY_PROGRAM_ID = "programId";
    private static final String ARTE_VIDEO_INFORMATION_URL_PATTERN = "https://api.arte.tv/api/player/v1/config/%s/%s?platform=ARTE_NEXT";
    private static final String ARTE_VIDEO_INFORMATION_URL_PATTERN_2 = "https://api.arte.tv/api/opa/v3/programs/%s/%s"; // Für broadcastBeginRounded
    private static final String JSON_ELEMENT_KEY_SHORT_DESCRIPTION = "shortDescription";
    private static final String JSON_ELEMENT_BROADCAST_ELTERNKNOTEN_1 = "programs";
    private static final String JSON_ELEMENT_BROADCAST_ELTERNKNOTEN_2 = "broadcastProgrammings";
    private static final String JSON_ELEMENT_BROADCAST = "broadcastBeginRounded";
    private static final String JSON_ELEMENT_BROADCASTTYPE = "broadcastType";
    private static final String JSON_ELEMENT_BROADCAST_CATCHUPRIGHTS_BEGIN = "catchupRightsBegin";
    private static final String JSON_ELEMENT_BROADCAST_CATCHUPRIGHTS_END = "catchupRightsEnd";
    private static final String BROADCASTTTYPE_FIRST = "FIRST_BROADCAST";
    private static final String BROADCASTTTYPE_MINOR_RE = "MINOR_REBROADCAST";
    private static final String BROADCASTTTYPE_MAJOR_RE = "MAJOR_REBROADCAST";
    
    private final JsonObject jsonObject;
    private final String langCode;
    private final String senderName;
    private final Calendar today;
    
    private final FastDateFormat broadcastDateFormat = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ssX");//2016-10-29T16:15:00Z
    
    public ArteJsonObjectToDatenFilmCallable(JsonObject aJsonObjec, String aLangCode, String aSenderName) {
        jsonObject = aJsonObjec;
        langCode = aLangCode;
        senderName = aSenderName;
        
        today = Calendar.getInstance();
    }

    @Override
    public DatenFilm call() {
        DatenFilm film = null;
        try {
        if(isValidProgramObject(jsonObject))
        {
            String titel = getTitle(jsonObject);
            String thema = getSubject(jsonObject);

            String beschreibung = getElementValue(jsonObject, JSON_ELEMENT_KEY_SHORT_DESCRIPTION);

            String urlWeb = getElementValue(jsonObject, JSON_ELEMENT_KEY_URL);

            //https://api.arte.tv/api/player/v1/config/[language:de/fr]/[programId]?platform=ARTE_NEXT
            String programId = getElementValue(jsonObject, JSON_ELEMENT_KEY_PROGRAM_ID);
            String videosUrl = String.format(ARTE_VIDEO_INFORMATION_URL_PATTERN, langCode, programId);

            Gson gson = new GsonBuilder().registerTypeAdapter(ArteVideoDTO.class, new ArteVideoDeserializer()).create();

            try(Response responseVideoDetails = executeRequest(videosUrl))
            {
                if(responseVideoDetails.isSuccessful())
                {
                    ArteVideoDTO video = gson.fromJson(responseVideoDetails.body().string(), ArteVideoDTO.class);

                    //The duration as time so it can be formatted and co.
                    LocalTime durationAsTime = durationAsTime(video.getDurationInSeconds());

                    if (video.getVideoUrls().containsKey(Qualities.NORMAL))
                    {
                        String broadcastBegin = getBroadcastBegin(gson, programId);                      

                        film = createFilm(thema, urlWeb, titel, video, broadcastBegin, durationAsTime, beschreibung);
                   } else {
                       LOG.debug(String.format("Keine \"normale\" Video URL für den Film \"%s\" mit der URL \"%s\". Video Details URL:\"%s\" ", titel, urlWeb, videosUrl));
                   }
                }
            } catch (IOException ioException)
            {
                LOG.error("Beim laden der Informationen eines Filmes für Arte kam es zu Verbindungsproblemen.", ioException);
            }
        }
        } catch(Exception e) {
            e.printStackTrace();
            LOG.error(e);
        }
        
        return film;
    }
    
    private String getBroadcastBegin(Gson gson, String programId) throws IOException {
        String broadcastBegin = "";
        
        OkHttpClient httpClient = MVHttpClient.getInstance().getHttpClient();
        //https://api.arte.tv/api/opa/v3/programs/[language:de/fr]/[programId]
        String videosUrlVideoDetails2 = String.format(ARTE_VIDEO_INFORMATION_URL_PATTERN_2, langCode, programId);
        Request request = new Request.Builder()
                .addHeader(MediathekArte_de.AUTH_HEADER, MediathekArte_de.AUTH_TOKEN)
                .url(videosUrlVideoDetails2).build();

        try(Response responseVideoDetails2 = httpClient.newCall(request).execute())
        {
          if(responseVideoDetails2.isSuccessful())
          {
            /*
             * Grobe Struktur des Json's:
             * {
             *  "meta": {}
             *  "programs": [{                   (JSON_ELEMENT_BROADCAST_ELTERNKNOTEN_1)
                  "broadcastProgrammings": [{    (JSON_ELEMENT_BROADCAST_ELTERNKNOTEN_2)
                    "broadcastBeginRounded": "2016-07-06T02:40:00Z",     (JSON_ELEMENT_BROADCAST)
                    "broadcastType": MAJOR_REBROADCAST, (JSON_ELEMENT_BROADCAST_TYPE)
                  ]}
                ]}
             * }
             */
            JsonObject jsonObjectVideoDetails2 = gson.fromJson(responseVideoDetails2.body().string(), JsonObject.class);
            if(jsonObjectVideoDetails2.isJsonObject() && 
                jsonObjectVideoDetails2.get(JSON_ELEMENT_BROADCAST_ELTERNKNOTEN_1).getAsJsonArray().size() > 0) {
                
                JsonObject programElement = jsonObjectVideoDetails2
                    .get(JSON_ELEMENT_BROADCAST_ELTERNKNOTEN_1).getAsJsonArray().get(0).getAsJsonObject();
                JsonArray broadcastArray = programElement.get(JSON_ELEMENT_BROADCAST_ELTERNKNOTEN_2).getAsJsonArray();
                
                if(broadcastArray.size() > 0) {
                    String broadcastBeginFirst = "";
                    String broadcastBeginMajor = "";
                    String broadcastBeginMinor = "";
                    
                    // nach Priorität der BroadcastTypen den relevanten Eintrag suchen
                    // FIRST_BROADCAST => MAJOR_REBROADCAST => MINOR_REBROADCAST
                    // dabei die "aktuellste" Ausstrahlung verwenden
                    for(int i = 0; i < broadcastArray.size(); i++) {
                        JsonObject broadcastObject = broadcastArray.get(i).getAsJsonObject();
                        if(broadcastObject.has(JSON_ELEMENT_BROADCASTTYPE) && 
                                broadcastObject.has(JSON_ELEMENT_BROADCAST)) {
                            String value = this.getBroadcastDate(broadcastObject);
                            if(!value.isEmpty()) {
                                String type = broadcastObject.get(JSON_ELEMENT_BROADCASTTYPE).getAsString();
                                switch(type) {
                                    case BROADCASTTTYPE_FIRST:
                                        broadcastBeginFirst = value;
                                        break;
                                    case BROADCASTTTYPE_MAJOR_RE:
                                        broadcastBeginMajor = value;
                                        break;
                                    case BROADCASTTTYPE_MINOR_RE:
                                        broadcastBeginMinor = value;
                                        break;
                                    default:
                                        LOG.debug("New broadcasttype: " + type);
                                }
                            }
                        }
                    }
                    
                    if(!broadcastBeginFirst.isEmpty()) {
                        broadcastBegin = broadcastBeginFirst;
                    } else if(!broadcastBeginMajor.isEmpty()) {
                        broadcastBegin = broadcastBeginMajor;
                    } else if(!broadcastBeginMinor.isEmpty()) {
                        broadcastBegin = broadcastBeginMinor;
                    }
                } else {
                    // keine Ausstrahlungen verfügbar => catchupRightsBegin verwenden
                    JsonElement elementBegin = programElement.get(JSON_ELEMENT_BROADCAST_CATCHUPRIGHTS_BEGIN);
                    if(!elementBegin.isJsonNull()) {
                        broadcastBegin = elementBegin.getAsString();
                    }
                }
            }
          }
        }      
        return broadcastBegin;
    }
    
    /**
     * Liefert den Beginn der Ausstrahlung, 
     * wenn 
     *  - heute im Zeitraum von CatchUpRights liegt 
     *  - oder heute vor dem Zeitraum liegt
     *  - oder CatchUpRights nicht gesetzt ist und die Ausstrahlung in der Vergangenheit liegt
     * @param broadcastObject 
     * @return der Beginn der Ausstrahlung oder ""
     */
    private String getBroadcastDate(JsonObject broadcastObject) {
        String broadcastDate = "";
        
        JsonElement elementBegin = broadcastObject.get(JSON_ELEMENT_BROADCAST_CATCHUPRIGHTS_BEGIN);
        JsonElement elementEnd = broadcastObject.get(JSON_ELEMENT_BROADCAST_CATCHUPRIGHTS_END);
        if (!elementBegin.isJsonNull() && !elementEnd.isJsonNull()) {
            String begin = elementBegin.getAsString();
            String end = elementEnd.getAsString();

            try {
                Calendar beginDate = Calendar.getInstance();
                beginDate.setTime(broadcastDateFormat.parse(begin));
                Calendar endDate = Calendar.getInstance();
                endDate.setTime(broadcastDateFormat.parse(end));

                if(DateWithoutTimeComparer.compare(today, beginDate) >= 0 && DateWithoutTimeComparer.compare(today, endDate) <= 0) {
                    // wenn das heutige Datum zwischen begin und end liegt,
                    // dann ist es die aktuelle Ausstrahlung
                    broadcastDate = broadcastObject.get(JSON_ELEMENT_BROADCAST).getAsString();
                } else if(DateWithoutTimeComparer.compare(today, beginDate) < 0) {
                    // ansonsten die zukünftige verwenden
                    broadcastDate = broadcastObject.get(JSON_ELEMENT_BROADCAST).getAsString();
                }
                
            } catch (ParseException ex) {
                LOG.debug(ex);
            }           
        } else {
            String broadcast = broadcastObject.get(JSON_ELEMENT_BROADCAST).getAsString();
            
            try {
                Calendar broadcastCal = Calendar.getInstance();
                broadcastCal.setTime(broadcastDateFormat.parse(broadcast));
                
                if(DateWithoutTimeComparer.compare(today, broadcastCal) >= 0) {
                    broadcastDate = broadcast;
                }
            } catch (ParseException ex) {
                LOG.debug(ex);
            }            
        }
        return broadcastDate;
    }
    
    private static String getSubject(JsonObject programObject) {
        String category = "";
        String subcategory = "";
        String subject;
        
        JsonElement catElement = programObject.get(JSON_ELEMENT_KEY_CATEGORY);
        if(!catElement.isJsonNull()) {
            JsonObject catObject = catElement.getAsJsonObject();
            category = catObject != null ? getElementValue(catObject, JSON_ELEMENT_KEY_NAME) : "";
        }
        
        JsonElement subcatElement = programObject.get(JSON_ELEMENT_KEY_SUBCATEGORY);
        if(!subcatElement.isJsonNull()) {
            JsonObject subcatObject = subcatElement.getAsJsonObject();
            subcategory = subcatObject != null ? getElementValue(subcatObject, JSON_ELEMENT_KEY_NAME) : "";
        }
       
        if(!category.equals(subcategory) && !subcategory.isEmpty()) {
            subject = category + " - " + subcategory;
        } else {
            subject = category;
        }

        return subject;
    }

    private static String getTitle(JsonObject programObject) {
        String title = getElementValue(programObject, JSON_ELEMENT_KEY_TITLE);
        String subtitle = getElementValue(programObject, JSON_ELEMENT_KEY_SUBTITLE);
                
        if (!title.equals(subtitle) && !subtitle.isEmpty()) {
            title = title + " - " + subtitle;
        }        
        
        return title;
    }

    private static boolean isValidProgramObject(JsonObject programObject) {
        return programObject.has(JSON_ELEMENT_KEY_TITLE) && 
            programObject.has(JSON_ELEMENT_KEY_PROGRAM_ID) && 
            programObject.has(JSON_ELEMENT_KEY_URL) &&
            !programObject.get(JSON_ELEMENT_KEY_TITLE).isJsonNull() &&
            !programObject.get(JSON_ELEMENT_KEY_PROGRAM_ID).isJsonNull() &&
            !programObject.get(JSON_ELEMENT_KEY_URL).isJsonNull();
    }
    
    private static String getElementValue(JsonObject jsonObject, String elementName) {
        return !jsonObject.get(elementName).isJsonNull() ? jsonObject.get(elementName).getAsString() : "";        
    }
    
    private DatenFilm createFilm(String thema, String urlWeb, String titel, ArteVideoDTO video, String broadcastBegin, LocalTime durationAsTime, String beschreibung) {
        
        String date = MserverDatumZeit.formatDate(broadcastBegin, broadcastDateFormat);
        String time = MserverDatumZeit.formatTime(broadcastBegin, broadcastDateFormat);
        
        DatenFilm film = new DatenFilm(senderName, thema, urlWeb, titel, video.getUrl(Qualities.NORMAL), "" /*urlRtmp*/,
                date, time, durationAsTime.toSecondOfDay(), beschreibung);
        if (video.getVideoUrls().containsKey(Qualities.HD))
        {
            CrawlerTool.addUrlHd(film, video.getUrl(Qualities.HD), "");
        }
        if (video.getVideoUrls().containsKey(Qualities.SMALL))
        {
            CrawlerTool.addUrlKlein(film, video.getUrl(Qualities.SMALL), "");
        }
        return film;
    }
    
    private Response executeRequest(String url) throws IOException {
        OkHttpClient httpClient = MVHttpClient.getInstance().getHttpClient();
        Request request = new Request.Builder().url(url).build();
                
        return httpClient.newCall(request).execute();   
    }
    
    private LocalTime durationAsTime(long aDurationInSeconds)
    {
        LocalTime localTime = LocalTime.MIN;
        
        localTime = localTime.plusSeconds(aDurationInSeconds);
        
        return localTime;
    }
}