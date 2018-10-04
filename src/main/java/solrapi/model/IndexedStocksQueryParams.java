package solrapi.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import alphavantageapi.model.StockPullOperation;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IndexedStocksQueryParams extends IndexedDocumentsQuery {
	private String[] symbols;
	private int[] numDaysPrevious;
	private String[] startDate;
	private String[] endDate;
	private String[] intervals;
	private String[] entityTypes;
	private int[] rows;
	private int[] pageNum;
	
	public String getQuery() {
		return getTimeRangeQuery("timestamp", startDate, endDate, numDaysPrevious);
	}

	public String[] getFilterQueries() {
		List<String> fqs = new ArrayList<String>();

		fqs.add(getFilterQuery("symbol", symbols));
		fqs.add(getFilterQuery("interval", intervals));
		
		return fqs.toArray(new String[fqs.size()]);
	}

	public int getQueryRows() {
		if (getRows() != null && getRows().length > 0) {
			return getRows()[0];
		} else {
			return Integer.MAX_VALUE;
		}
	}

	public int getQueryStart() {
		if (getPageNum() != null && getPageNum().length > 0) {
			return (getPageNum()[0] - 1) * getQueryRows();
		} else {
			return 0;
		}
	}

	public Map<String, StockPullOperation> getStockPullOperations() {
		Map<String, StockPullOperation> stockPullOperations = new HashMap<>();
		if (symbols != null && intervals != null) {
			for (String symbol : symbols) {
				for (String interval : intervals) {
					stockPullOperations.put(symbol, new StockPullOperation(interval));
				}
			}
		}
		return stockPullOperations;
	}
	
	public String[] getSymbols() {
		return symbols;
	}
	public void setSymbols(String[] symbols) {
		this.symbols = symbols;
	}
	public int[] getNumDaysPrevious() {
		return numDaysPrevious;
	}
	public void setNumDaysPrevious(int[] numDaysPrevious) {
		this.numDaysPrevious = numDaysPrevious;
	}
	public String[] getStartDate() {
		return startDate;
	}
	public void setStartDate(String[] startDate) {
		this.startDate = startDate;
	}
	public String[] getEndDate() {
		return endDate;
	}
	public void setEndDate(String[] endDate) {
		this.endDate = endDate;
	}
	public String[] getIntervals() {
		return intervals;
	}
	public void setIntervals(String[] intervals) {
		this.intervals = intervals;
	}

	public int[] getRows() {
		return rows;
	}

	public void setRows(int[] rows) {
		this.rows = rows;
	}

	public int[] getPageNum() {
		return pageNum;
	}

	public void setPageNum(int[] pageNum) {
		this.pageNum = pageNum;
	}

	public String[] getEntityTypes() {
		return entityTypes;
	}

	public void setEntityTypes(String[] entityTypes) {
		this.entityTypes = entityTypes;
	}
}
