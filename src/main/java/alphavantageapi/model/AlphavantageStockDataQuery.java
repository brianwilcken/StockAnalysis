package alphavantageapi.model;

public class AlphavantageStockDataQuery {

    private String function;
    private String symbol;
    private String interval;
    private String outputsize = "full";
    private String apikey;

    public AlphavantageStockDataQuery(String function, String symbol, String interval, String apikey) {
        this.function = function;
        this.symbol = symbol;
        this.interval = interval;
        this.apikey = apikey;
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getInterval() {
        return interval;
    }

    public void setInterval(String interval) {
        this.interval = interval;
    }

    public String getOutputsize() {
        return outputsize;
    }

    public void setOutputsize(String outputsize) {
        this.outputsize = outputsize;
    }

    public String getApikey() {
        return apikey;
    }

    public void setApikey(String apikey) {
        this.apikey = apikey;
    }
}
