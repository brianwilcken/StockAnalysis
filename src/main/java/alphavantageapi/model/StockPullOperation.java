package alphavantageapi.model;

public class StockPullOperation {
    private String function;
    private String interval;

    public StockPullOperation(String function, String interval) {
        this.function = function;
        this.interval = interval;
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
