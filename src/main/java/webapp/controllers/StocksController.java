package webapp.controllers;

import alphavantageapi.AlphavantageClient;
import alphavantageapi.model.StockPullOperation;
import common.Tools;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import solrapi.SolrClient;
import solrapi.model.IndexedStocksQueryParams;
import webapp.models.JsonResponse;
import webapp.services.RefreshStockDataService;
import webscraper.WebClient;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CrossOrigin
@RestController
@RequestMapping("/api/stocks")
public class StocksController {

    private SolrClient solrClient;
    private WebClient webClient;
    private AlphavantageClient alphavantageClient;
    private List<Exception> exceptions;

    final static Logger logger = LogManager.getLogger(StocksController.class);

    @Autowired
    private RefreshStockDataService refreshStockDataService;

    @Autowired
    private HttpServletRequest context;

    public StocksController() {
        webClient = new WebClient();
        alphavantageClient = new AlphavantageClient();
        solrClient = new SolrClient(Tools.getProperty("solr.url"));
        exceptions = new ArrayList<>();
    }

    public WebClient getWebClient() { return webClient; }

    public AlphavantageClient getAlphaVantageClient() { return alphavantageClient; }

    @PostConstruct
    public void initRefreshStockDataProcess() {
        try {
            logger.info("Initializing stock data refresh process");
            refreshStockDataService.refreshStockData(this);
        } catch (Exception e) {}
    }

    @PostConstruct
    public void initRefreshStockNewsProcess() {
        try {
            logger.info("Initializing stock news refresh process");
            refreshStockDataService.refreshStockNews(this);
        } catch (Exception e) {}
    }

    public void refreshStockDataProcessExceptionHandler(Exception e) {
        Tools.getExceptions().add(e);
        try {
            refreshStockDataService.refreshStockData(this);
        } catch (Exception e1) {}
    }

    public void refreshStockNewsProcessExceptionHandler(Exception e) {
        Tools.getExceptions().add(e);
        try {
            refreshStockDataService.refreshStockNews(this);
        } catch (Exception e1) {}
    }

    @RequestMapping(method= RequestMethod.GET, produces= MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonResponse> getStocks(IndexedStocksQueryParams params) {
        logger.info(context.getRemoteAddr() + " -> " + "In getStocks method");
        try {
            int totalStocks = 0;
            Map<String, StockPullOperation> stockPullOperationMap = params.getStockPullOperations();
            for (String symbol : stockPullOperationMap.keySet()) {
                totalStocks += alphavantageClient.QueryStockData(symbol, stockPullOperationMap.get(symbol));
            }
            return ResponseEntity.ok().body(Tools.formJsonResponse(totalStocks, params.getQueryTimeStamp()));
        } catch (Exception e) {
            logger.error(context.getRemoteAddr() + " -> " + e);
            Tools.getExceptions().add(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null, params.getQueryTimeStamp()));
        }
    }
}
