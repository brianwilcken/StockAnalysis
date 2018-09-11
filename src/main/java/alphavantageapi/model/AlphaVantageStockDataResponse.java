package alphavantageapi.model;

import solrapi.model.IndexedStock;

import java.util.*;

public class AlphaVantageStockDataResponse extends HashMap<String, Object> {

    public List<IndexedStock> ingestJSON(String symbol, String interval) {
        String timeSeries = "Time Series (" + interval + ")";
        LinkedHashMap<String, Object> timeSeriesData = (LinkedHashMap<String, Object>)get(timeSeries);

        List<IndexedStock> stocks = new ArrayList<>();
        for (Entry<String, Object> entry : timeSeriesData.entrySet()) {
            IndexedStock stock = new IndexedStock();
            stock.consume(symbol, interval, entry);
            stock.initId();
            stocks.add(stock);
        }
        return stocks;
    }
}
