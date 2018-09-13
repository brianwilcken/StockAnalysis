package webapp.services;

import alphavantageapi.AlphavantageClient;
import alphavantageapi.model.StockPullInstructions;
import alphavantageapi.model.StockPullOperation;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import common.Tools;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import webapp.controllers.StocksController;
import webscraper.WebClient;

import java.io.InputStream;
import java.util.List;

@Service
public class RefreshStockDataService {

	final static Logger logger = LogManager.getLogger(RefreshStockDataService.class);

    private final Boolean avAutoRefresh = Boolean.parseBoolean(Tools.getProperty("alphavantage.autoRefresh"));
    private final Boolean wsAutoRefresh = Boolean.parseBoolean(Tools.getProperty("webScraper.autoRefresh"));

	private final int BASE_TIMEOUT = 15000; //milliseconds
    private final int ALPHAVANTAGE_INTERVAL = Integer.parseInt(Tools.getProperty("alphavantage.refreshInterval"));
    private final int WEB_SCRAPER_INTERVAL = Integer.parseInt(Tools.getProperty("webScraper.refreshInterval"));

    private static class RefreshTimer{
        private final int interval;
        private final Tools.CheckedConsumer<StocksController> refresher;
        private final StocksController StocksController;
        private final Boolean autoRefresh;

        private int totalElapsed = 0;

        public RefreshTimer(int interval, Tools.CheckedConsumer<StocksController> refresher, StocksController StocksController, Boolean autoRefresh) {
            this.interval = interval;
            this.refresher = refresher;
            this.StocksController = StocksController;
            this.autoRefresh = autoRefresh;
        }

        public void refresh(int millis) throws Exception {
            if (autoRefresh) {
                totalElapsed += millis;

                if (totalElapsed >= interval) {
                    refresher.apply(StocksController);
                    totalElapsed = 0;
                }
            }
        }

        public void execute() throws Exception {
            if (autoRefresh) {
                refresher.apply(StocksController);
            }
        }
    }

	@Async("processExecutor")
    public void refreshStockData(StocksController StocksController) throws Exception {
        try {
            RefreshTimer avRefresher = new RefreshTimer(ALPHAVANTAGE_INTERVAL, this::refreshAlphavantage, StocksController, avAutoRefresh);
            avRefresher.execute();
            while(true) {
                Thread.sleep(BASE_TIMEOUT);
                avRefresher.refresh(BASE_TIMEOUT);
            }
        } catch (Exception e) {
            StocksController.refreshStockDataProcessExceptionHandler(e);
            throw e;
        }
    }

    @Async("processExecutor")
    public void refreshStockNews(StocksController StocksController) throws Exception {
        try {
            RefreshTimer wsRefresher = new RefreshTimer(WEB_SCRAPER_INTERVAL, this::refreshWebScraper, StocksController, wsAutoRefresh);
            wsRefresher.execute();
            while(true) {
                Thread.sleep(BASE_TIMEOUT);
                wsRefresher.refresh(BASE_TIMEOUT);
            }
        } catch (Exception e) {
            StocksController.refreshStockNewsProcessExceptionHandler(e);
            throw e;
        }
    }

    private void refreshAlphavantage(StocksController stocksService) throws Exception {
        AlphavantageClient client = stocksService.getAlphaVantageClient();

        ClassPathResource instructionsFile = new ClassPathResource(Tools.getProperty("alphavantage.stockPullInstructions"));
        InputStream instructionsStream = instructionsFile.getInputStream();
        MappingIterator<StockPullInstructions> instructionsIter = new CsvMapper().readerWithTypedSchemaFor(StockPullInstructions.class).readValues(instructionsStream);
        List<StockPullInstructions> instructionsList = instructionsIter.readAll();
        for (StockPullInstructions instructions : instructionsList) {
            List<StockPullOperation> operations = instructions.getOperations();
            for (StockPullOperation operation : operations) {
                try {
                    client.QueryStockData(instructions.symbol, operation);
                    Thread.sleep(12000);
                } catch (SolrServerException e) {
                    logger.fatal(e.getMessage(), e);
                    throw e; //a Solr error warrants termination of the current process
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                    Thread.sleep(60000);
                    continue; //any other exception could just be a refused connection... try again in 60s
                }
            }
        }

    }

    private void refreshWebScraper(StocksController stocksService) throws Exception {
        ClassPathResource instructionsFile = new ClassPathResource(Tools.getProperty("alphavantage.stockPullInstructions"));
        InputStream instructionsStream = instructionsFile.getInputStream();
        MappingIterator<StockPullInstructions> instructionsIter = new CsvMapper().readerWithTypedSchemaFor(StockPullInstructions.class).readValues(instructionsStream);
        List<StockPullInstructions> instructionsList = instructionsIter.readAll();
        for (StockPullInstructions instructions : instructionsList) {
            logger.info("Start Scraping!!!");
            WebClient client = stocksService.getWebClient();
            int totalScraped = client.queryGoogle(instructions.symbol, WebClient.QUERY_TIMEFRAME_LAST_HOUR, client::processSearchResult);
            logger.info("Number of news articles scraped from the web: " + totalScraped);
        }
    }
}
