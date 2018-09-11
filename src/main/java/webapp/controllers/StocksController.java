package webapp.controllers;

import alphavantageapi.AlphavantageClient;
import common.Tools;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import solrapi.SolrClient;
import solrapi.model.IndexedStock;
import solrapi.model.IndexedStocksQueryParams;
import webapp.models.JsonResponse;
import webapp.services.RefreshStockDataService;
import webscraper.WebClient;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

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
            refreshStockDataService.process(this);
        } catch (Exception e) {}
    }

    public void refreshStockDataProcessExceptionHandler(Exception e) {
        Tools.getExceptions().add(e);
        try {
            refreshStockDataService.process(this);
        } catch (Exception e1) {}
    }

//    @RequestMapping(method= RequestMethod.GET, produces= MediaType.APPLICATION_JSON_VALUE)
//    public ResponseEntity<JsonResponse> getStocks(IndexedStocksQueryParams params) {
//        logger.info(context.getRemoteAddr() + " -> " + "In getStocks method");
//        try {
//            SortClause sort = new SortClause("lastUpdated", "desc");
//            List<IndexedEvent> events = solrClient.QueryIndexedDocuments(IndexedStock.class, params.getQuery(), params.getQueryRows(), params.getQueryStart(), sort, params.getFilterQueries());
//            JsonResponse response;
//            if (params.getIncludeDeletedIds() != null && params.getIncludeDeletedIds()) {
//                logger.info(context.getRemoteAddr() + " -> " + "Including deleted ids");
//                //Obtain set of deleted event Ids
//                List<String> deletedEventIds = events.stream()
//                        .filter(p -> p.getEventState() != null && p.getEventState().compareTo(SolrConstants.Events.EVENT_STATE_DELETED) == 0)
//                        .map(p -> p.getId())
//                        .collect(Collectors.toList());
//                logger.info(context.getRemoteAddr() + " -> " + "Total number of deleted events: " + deletedEventIds.size());
//
//                //Filter to produce list of non-deleted events
//                List<IndexedEvent> nonDeletedEvents = events.stream().filter(p -> p.getEventState() != null &&
//                        p.getEventState().compareTo(SolrConstants.Events.EVENT_STATE_DELETED) != 0).collect(Collectors.toList());
//
//                logger.info(context.getRemoteAddr() + " -> " + "Total number of non-deleted events: " + nonDeletedEvents.size());
//                //form response with deleted event ids
//                response = Tools.formJsonResponse(nonDeletedEvents, params.getQueryTimeStamp());
//                response.setDeletedEvents(deletedEventIds);
//            } else {
//                response = Tools.formJsonResponse(events, params.getQueryTimeStamp());
//            }
//
//            logger.info(context.getRemoteAddr() + " -> " + "Returning events");
//            return ResponseEntity.ok().body(response);
//        } catch (Exception e) {
//            logger.error(context.getRemoteAddr() + " -> " + e);
//            Tools.getExceptions().add(e);
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Tools.formJsonResponse(null, params.getQueryTimeStamp()));
//        }
//    }
}
