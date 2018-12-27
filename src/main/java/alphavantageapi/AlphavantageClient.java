package alphavantageapi;

import alphavantageapi.model.AlphavantageStockDataResponse;
import alphavantageapi.model.AlphavantageStockDataQuery;
import alphavantageapi.model.StockPullOperation;
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
            client.QueryStockData("MSFT", new StockPullOperation("60min"));
        } catch (SolrServerException e) {
            e.printStackTrace();
        }
    }

    public AlphavantageClient() {
        solrClient = new SolrClient(solrUrl);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        restTemplate = new RestTemplate(requestFactory);
    }

    public int QueryStockData(String symbol, StockPullOperation op) throws SolrServerException {
        logger.info("Now querying Alphavantage for symbol: " + symbol + ", time series: " + op.getFunction() + ", interval: " + op.getInterval());
        AlphavantageStockDataQuery query = new AlphavantageStockDataQuery(op.getFunction(), symbol, op.getInterval(), apiKey);
        String url = alphavantageQueryUrl + Tools.GetQueryString(query);
        ResponseEntity<AlphavantageStockDataResponse> response = restTemplate.getForEntity(url, AlphavantageStockDataResponse.class);

        AlphavantageStockDataResponse respBody = response.getBody();
        List<IndexedStock> stocks = respBody.ingestJSON(symbol, op.getInterval());

        solrClient.indexDocuments(stocks);
        int totalStocks = stocks.size();

        logger.info(totalStocks + " stock ticks indexed for symbol: " + symbol + ", time series: " + op.getFunction() + ", interval: " + op.getInterval());
        return totalStocks;
    }
}
