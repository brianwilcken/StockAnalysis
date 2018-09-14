package alphavantageapi.model;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

@JsonPropertyOrder({ "exchange", "symbol", "instructions" })
public class StockPullInstructions {
    public String exchange;
    public String symbol;
    public String instructions;

    public List<StockPullOperation> getOperations() {
        List<StockPullOperation> stockPullOperations = new ArrayList<>();
        String[] operations = instructions.split("\\|");
        for (String operation : operations) {
            String[] operands = operation.split(":");
            String function = operands[0];
            String interval = operands[1];
            StockPullOperation stockPullOperation = new StockPullOperation(function, interval);
            stockPullOperations.add(stockPullOperation);
        }
        return stockPullOperations;
    }
}
