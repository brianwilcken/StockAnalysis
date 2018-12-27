# -*- coding: utf-8 -*-
"""
Created on Wed Sep 12 10:02:49 2018

@author: WILCBM
"""

"""
In order to predict the next closing price it is necessary to estimate the other data columns.
Using a predictive model to determine the values for the open, high and low columns can be
useful too provided suitable estimates are supplied for the other columns.

Step 1: generate and train a predictive model to fit data for the close price.

Step 2: provide optimized estimations for the next value over for open, high, low and volume columns.

Step 3: Using the estimates predict the value of the close price using the model from step 1.
   
Estimation of future data points is performed using an exponential moving average approach.
We can optimize the span of the moving average using an iterative approach.  We iterate over
an appropriate interval of span sizes while forming estimates against known data points.
By comparing estimated values against the truth we can calculate error for each estimate, and 
determine a distribution of error points across the estimation range and for a given span.  By
minimizing the standard deviation of this distribution we can find the best estimation span for a
given data series.

"""

import os

os.chdir('\\\\Desktop-ltmq880\\c\\code\\StockAnalysis\\PythonCode')

import tensorflow as tf
from sklearn.model_selection import train_test_split
import numpy as np
import plot_tools as plt
import common as com
import tf_keras_models as mod
from tensorflow import keras
import pickle

symbol = 'VTVT'
interval = '1min'

model_file_path = './models/' + symbol + '_' + interval + '/model.h5'
norm_file_path = './norm_factors/' + symbol + '_' + interval + '/norm.pkl'
perf_file_path = './perf_metrics/' + symbol + '_' + interval + '/perf.pkl'

os.makedirs(os.path.dirname(model_file_path), exist_ok=True)
os.makedirs(os.path.dirname(norm_file_path), exist_ok=True)
os.makedirs(os.path.dirname(perf_file_path), exist_ok=True)

com.init_stock_data_update(symbol, interval)
stock_data = com.pull_stock_data(symbol, interval)

plt.plot_stock_activity(stock_data, symbol + ' ' + interval, flip=True)

##The latest quote may represent incomplete data.  This will be used for closing price estimation.
#latest_quote = stock_data.head(1)
#stock_data = stock_data.tail(len(stock_data)-1)
#latest_volume = latest_quote['Volume'].values[0]
#
##Produce estimate of the volume data point.
#est_data = com.modify_for_estimation(stock_data)
#volSpan = com.minimize_estimation_error(est_data['Volume'])
#estVol = com.estimate_next_data_point(est_data['Volume'], volSpan).tail(1).values[0]
#
##TODO: apply volume modifer based on news sentiment analysis
#
##produce the array of points that will be used to predict the next closing price
#if estVol > latest_quote['Volume'].values[0]:
#    latest_quote['Volume'] = np.mean([estVol, latest_quote['Volume']])
#latest_quote = latest_quote.drop('Close', axis=1).values

#set aside 20% of data as a blind test
stock_data, blind_test_data = com.modify_for_blind_test(stock_data, 0.2)

#extract training labels based on a specific data column
data, labels = com.extract_labels(stock_data, 'Volume')
blind_data, blind_labels = com.extract_labels(blind_test_data, 'Volume')

#split the data apart into training and test sets
test_size = np.floor(len(data)*0.2).astype('int')
data_train, data_test, labels_train, labels_test = train_test_split(data[::-1], labels[::-1], test_size=test_size, shuffle=True)
data_train = data_train[::-1]
data_test = data_test[::-1]
labels_train = labels_train[::-1]
labels_test = labels_test[::-1]

#normalize the training and test data
mean = data_train.mean(axis=0)
std = data_train.std(axis=0)
data_train = (data_train - mean) / std
data_test = (data_test - mean) / std
blind_data = (blind_data - mean) / std
#latest_quote_norm = (latest_quote - mean) / std

#save the normalization vars for later use
with open(norm_file_path, 'wb') as f:
    pickle.dump([mean, std], f)

#fit the training data to the model
with tf.Session(config=tf.ConfigProto(log_device_placement=True, device_count={'CPU' : 1, 'GPU' : 0})) as sess:
    sess.run(tf.global_variables_initializer())
    
    #build and compile the model
    if symbol in mod.modelFuncs and interval in mod.modelFuncs[symbol]:
        model, epochs, early_stop_patience = mod.modelFuncs[symbol][interval]()
    else:
        model, epochs, early_stop_patience = mod.get_generic_model()
    
    early_stop = keras.callbacks.EarlyStopping(monitor='val_loss', patience=early_stop_patience)
    history = model.fit(data_train, labels_train, epochs=epochs,
                        validation_data=(data_test, labels_test), verbose=1, callbacks=[early_stop])
    
    #plot model performance data
    plt.plot_history(history)
    error = plt.plot_prediction_accuracy_metrics(model, blind_data, blind_labels)
    predicted = plt.plot_true_versus_predicted_activity(model, blind_data, blind_labels, 'Volume')
    mae = com.get_model_mean_absolute_error(model, blind_data, blind_labels)
    
    mean_error = np.mean(error)
    #predicted_future_closing_price = model.predict(latest_quote_norm)
    #predicted_future_closing_price_corrected = predicted_future_closing_price - mean_error
    
#    #save model performance metrics
#    with open(perf_file_path, 'wb') as f:
#        pickle.dump([mean_error, mae], f)
#    
#    #save the model for later use
#    model.save(model_file_path)

    