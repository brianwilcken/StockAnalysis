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

stock_analysis_types = {
            'day': { 'function': 'TIME_SERIES_DAILY', 'interval': '0', 'keyVal': 'Time Series (Daily)' },
            'hour': { 'function': 'TIME_SERIES_INTRADAY', 'interval': '60min', 'keyVal': 'Time Series (60min)' },
            'minute5': { 'function': 'TIME_SERIES_INTRADAY', 'interval': '5min', 'keyVal': 'Time Series (5min)' },
            'minute1': { 'function': 'TIME_SERIES_INTRADAY', 'interval': '1min', 'keyVal': 'Time Series (1min)' }
        }

#get the stock data from alpha vantage
def pull_stock_data(symbol, stock_analysis_type):
    url = 'https://www.alphavantage.co/query?function=' + stock_analysis_type['function'] + '&symbol=' + symbol + '&interval=' + stock_analysis_type['interval'] + '&outputsize=full&apikey=Y2ELRYUSN2FEB51P'
    response = requests.get(url)
    stock_data = json.loads(response.content)
    stock_data = pd.DataFrame([entry for entry in stock_data[stock_analysis_type['keyVal']].values()])
    stock_data.columns=['Open', 'High', 'Low', 'Close', 'Volume']
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