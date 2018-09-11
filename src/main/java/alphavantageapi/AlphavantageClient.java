package alphavantageapi;

import alphavantageapi.model.AlphaVantageStockDataResponse;
import alphavantageapi.model.AlphavantageStockDataQuery;
import common.Tools;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import solrapi.SolrClient;
import solrapi.model.IndexedStock;

import java.util.List;

public class AlphavantageClient {
    final static Logger logger = LogManager.getLogger(AlphavantageClient.class);

    private RestTemplate restTemplate;
    private SolrClient solrClient;
    private String alphavantageQueryUrl = Tools.getProperty("alphavantage.url");
    private String solrUrl = Tools.getProperty("solr.url");
    private String apiKey = Tools.getProperty("alphavantage.apiKey");

    public static void main(String[] args) {
        AlphavantageClient client = new AlphavantageClient();
        client.alphavantageQueryUrl = "https://www.alphavantage.co/query";
        client.solrUrl = "http://localhost:8983/solr";
        client.apiKey = "Y2ELRYUSN2FEB51P";
        client.solrClient = new SolrClient(client.solrUrl);

        try {
            client.QueryStockData("NNDM", "TIME_SERIES_DAILY", "Daily");
        } catch (SolrServerException e) {
            e.printStackTrace();
        }
    }

    public AlphavantageClient() {
        solrClient = new SolrClient(solrUrl);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        restTemplate = new RestTemplate(requestFactory);
    }

    public int QueryStockData(String symbol, String function, String interval) throws SolrServerException {
        logger.info("Now querying Alphavantage for symbol: " + symbol + ", time series: " + function + ", interval: " + interval);
        AlphavantageStockDataQuery query = new AlphavantageStockDataQuery(function, symbol, interval, apiKey);
        String url = alphavantageQueryUrl + Tools.GetQueryString(query);
        ResponseEntity<AlphaVantageStockDataResponse> response = restTemplate.getForEntity(url, AlphaVantageStockDataResponse.class);

        AlphaVantageStockDataResponse respBody = response.getBody();
        List<IndexedStock> stocks = respBody.ingestJSON(symbol, interval);

        solrClient.indexDocuments(stocks);
        int totalStocks = stocks.size();

        logger.info(totalStocks + " stocks indexed for symbol: " + symbol + ", time series: " + function + ", interval: " + interval);
        return totalStocks;
    }
}
