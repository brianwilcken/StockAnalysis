# -*- coding: utf-8 -*-
"""
Created on Mon Sep 10 12:19:40 2018

@author: WILCBM
"""
import pandas as pd
import requests
import json
import numpy as np
from tensorflow import keras

class PrintDot(keras.callbacks.Callback):
    def on_epoch_end(self, epoch, logs):
        if epoch % 100 == 0: 
            print('')
        print('.', end='')

def init_stock_data_update(symbol, interval):
    url = 'http://localhost:8080/stockAnalysis/api/stocks?symbols=' + symbol + '&intervals=' + interval
    response = requests.get(url)
    update_details = json.loads(response.content)
    
    return update_details
    

#get the stock data from solr
def pull_stock_data(symbol, interval):
    url = 'http://localhost:8983/solr/stocks/select?q=symbol:' + symbol + ' AND interval:' + interval + '&sort=timestamp%20DESC&rows=1000000&wt=json'
    response = requests.get(url)
    stock_data = json.loads(response.content)
    stock_data = pd.DataFrame([entry for entry in stock_data['response']['docs']])
    stock_data = stock_data.drop('_version_', axis=1).drop('id', axis=1).drop('interval', axis=1).drop('symbol', axis=1).drop('timestamp', axis=1)
    stock_data.columns=['Close', 'High', 'Low', 'Open', 'Volume']
    stock_data[:] = stock_data[:].astype('float64')
    
    return stock_data

def pull_data_by_interval(interval):
    url = 'http://localhost:8983/solr/stocks/select?q=interval:' + interval + '&rows=1000000&wt=json'
    response = requests.get(url)
    stock_data = json.loads(response.content)
    stock_data = pd.DataFrame([entry for entry in stock_data['response']['docs']])
    stock_data = stock_data.drop('_version_', axis=1).drop('id', axis=1).drop('interval', axis=1).drop('symbol', axis=1).drop('timestamp', axis=1)
    stock_data.columns=['Close', 'High', 'Low', 'Open', 'Volume']
    stock_data[:] = stock_data[:].astype('float64')
    
    return stock_data
    
def modify_for_blind_test(stock_data, percent):
    blind_test_data = stock_data.head(np.floor(len(stock_data)*percent).astype('int'))
    drop_indices = stock_data.head(np.floor(len(stock_data)*percent).astype('int')).index
    stock_data = stock_data.drop(drop_indices)
    
    return stock_data, blind_test_data[::-1]

def extract_labels(data, label):
    labels = data[label].values
    data = data.drop(label, axis=1).values
    
    return data, labels

def get_model_mean_absolute_error(model, data_test, labels_test):
    loss, mae = model.evaluate(data_test, labels_test, verbose=0)
    
    return mae

def modify_for_estimation(stock_data):
    est_data = pd.DataFrame(stock_data.reindex(index=stock_data.index[::-1]).values)
    est_data.columns=['Close', 'High', 'Low', 'Open', 'Volume']
    
    return est_data

def estimate_next_data_point(series, span=5):
    num_points = len(series)
    np_array = series.values
    np_array = np.append(np_array, np_array[num_points - span])
    series = pd.Series(np_array)
    series[num_points] = series.ewm(span=span).mean()[num_points]
    
    return series

def estimate_next_n_points(series, span=5, n=3):
    for i in range (0, n):
        series = estimate_next_data_point(series, span)
        
    return series
        
def evaluate_estimation_error(series, rng=10, span=5):
    num_points = len(series)
    test_series = series[0:num_points - rng]
    errs = {}
    for i in range(num_points - rng, num_points):
        test_series = estimate_next_data_point(test_series, span)
        err = (series[i] - test_series[i])/series[i]
        errs[i] = { 'error' : err, 'true' : series[i], 'estimated' : test_series[i] }
        test_series[i] = series[i]
    
    return errs

def minimize_estimation_error(series, maxSpan=40, rng=100):
    std_devs = {}
    for n in range(2, maxSpan):
        errs = evaluate_estimation_error(series, rng=rng, span=n)
        std_dev = np.std([error['error'] for error in errs.values()])
        std_devs[n] = std_dev
    minSpan = min(std_devs, key=std_devs.get)
    
    return minSpan
    