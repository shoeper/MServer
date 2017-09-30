package de.mediathekview.mserver.crawler.dreisat;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import de.mediathekview.mlib.daten.Film;
import de.mediathekview.mlib.daten.Sender;
import de.mediathekview.mlib.messages.listener.MessageListener;
import de.mediathekview.mserver.crawler.basic.AbstractCrawler;
import de.mediathekview.mserver.crawler.basic.CrawlerUrlsDTO;
import de.mediathekview.mserver.crawler.dreisat.tasks.DreisatOverviewpageTask;
import de.mediathekview.mserver.progress.listeners.SenderProgressListener;

public class DreiSatCrawler extends AbstractCrawler {
  private static final String SENDUNG_VERPASST_BASE_URL =
      "http://www.3sat.de/mediathek/?mode=verpasst";
  private static final String SENDUNGEN_AZ_URL = "http://www.3sat.de/mediathek/?mode=sendungenaz";

  public DreiSatCrawler(final ForkJoinPool aForkJoinPool,
      final Collection<MessageListener> aMessageListeners,
      final Collection<SenderProgressListener> aProgressListeners) {
    super(aForkJoinPool, aMessageListeners, aProgressListeners);
  }

  @Override
  public Sender getSender() {
    return Sender.DREISAT;
  }

  private ConcurrentLinkedQueue<CrawlerUrlsDTO> getSendungenAZUrls() {
    final ConcurrentLinkedQueue<CrawlerUrlsDTO> sendungUrls = new ConcurrentLinkedQueue<>();
    sendungUrls.add(new CrawlerUrlsDTO(SENDUNGEN_AZ_URL));
    return sendungUrls;
  }

  private ConcurrentLinkedQueue<CrawlerUrlsDTO> getSendungVerpasstUrls() {
    final ConcurrentLinkedQueue<CrawlerUrlsDTO> sendungVerpasstUrls = new ConcurrentLinkedQueue<>();
    sendungVerpasstUrls.add(new CrawlerUrlsDTO(SENDUNG_VERPASST_BASE_URL));
    return sendungVerpasstUrls;
  }

  @Override
  protected RecursiveTask<Set<Film>> createCrawlerTask() {

    final DreisatOverviewpageTask sendungenTask = new DreisatOverviewpageTask(this,
        getSendungenAZUrls(), false, config.getMaximumDaysForSendungVerpasstSection());
    final Set<CrawlerUrlsDTO> sendungUrls = forkJoinPool.invoke(sendungenTask);


    final DreisatOverviewpageTask sendungVerpasstTask = new DreisatOverviewpageTask(this,
        getSendungVerpasstUrls(), true, config.getMaximumSubpages());
    final Set<CrawlerUrlsDTO> sendungVerpasstFilmUrls = forkJoinPool.invoke(sendungVerpasstTask);


    // TODO Auto-generated method stub
    return null;
  }

}
