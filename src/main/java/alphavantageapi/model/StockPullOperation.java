package alphavantageapi.model;

import java.util.HashMap;
import java.util.Map;

public class StockPullOperation {
    private String function;
    private String interval;

    private static final Map<String, String> funcMap = createMap();
    private static Map<String, String> createMap()
    {
        Map<String,String> map = new HashMap<>();
        map.put("Daily", "TIME_SERIES_DAILY");
        map.put("1min", "TIME_SERIES_INTRADAY");
        return map;
    }

    public StockPullOperation(String function, String interval) {
        this.function = function;
        this.interval = interval;
    }

    public StockPullOperation(String interval) {
        this.interval = interval;
        this.function = funcMap.get(interval);
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }
}
