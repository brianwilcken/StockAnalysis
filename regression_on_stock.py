# -*- coding: utf-8 -*-
"""
Created on Thu Aug 30 10:36:35 2018

@author: WILCBM
"""

import pandas as pd
import tensorflow as tf
from tensorflow import keras
from sklearn.model_selection import train_test_split
import numpy as np
import matplotlib.pyplot as plt

def get_stock_data_set(csvPath, label):
    stock_data = pd.read_csv(csvPath, sep=',')
    
    labels = stock_data[label].values
    data = stock_data.drop(label, axis=1).drop('date', axis=1).values
    
    return (data, labels)

def normalize_features(train_data, test_data=None):
    mean = train_data.mean(axis=0)
    std = train_data.std(axis=0)
    
    train_data = (train_data - mean) / std
    if test_data is not None:
        test_data = (test_data - mean) / std
        return (train_data, test_data)
    else:
        return train_data
    
def build_model():
    model = keras.Sequential([
        keras.layers.Dense(4, activation=tf.nn.relu, kernel_regularizer=keras.regularizers.l2(0.001)),
        keras.layers.Dense(1)
      ])
    
    optimizer = tf.train.AdamOptimizer()
    
    model.compile(loss='mse',
                optimizer=optimizer,
                metrics=['mae'])
    
    return model

class PrintDot(keras.callbacks.Callback):
    def on_epoch_end(self, epoch, logs):
        if epoch % 100 == 0: 
            print('')
        print('.', end='')
    
def plot_history(history):
  plt.figure()
  plt.xlabel('Epoch')
  plt.ylabel('Mean Abs Error')
  plt.plot(history.epoch, np.array(history.history['mean_absolute_error']),
           label='Train Loss')
  plt.plot(history.epoch, np.array(history.history['val_mean_absolute_error']),
           label = 'Val loss')
  plt.legend()
  #plt.ylim([0, 250])
    
def train_test_split_for_stocks(data, labels, test_size):
    data_train, data_test, labels_train, labels_test = train_test_split(data[::-1], labels[::-1], test_size=test_size, shuffle=False)
    data_train = data_train[::-1]
    data_test = data_test[::-1]
    labels_train = labels_train[::-1]
    labels_test = labels_test[::-1]
    
    return data_train, data_test, labels_train, labels_test
  
def plot_prediction_accuracy_metrics(model, data_test, close_prices_test):
    close_prices_pred = model.predict(data_test)
    close_prices_pred = close_prices_pred.reshape(-1,)
    
    plt.figure()
    plt.scatter(close_prices_test, close_prices_pred)
    plt.xlabel('True Values')
    plt.ylabel('Predictions')
    plt.axis('equal')
    plt.xlim(plt.xlim())
    plt.ylim(plt.ylim())
    plt.plot([-2000, 2000], [-2000, 2000])
    
    error = close_prices_pred - close_prices_test
    plt.figure()
    plt.hist(error, bins = 30)
    plt.xlabel("Prediction Error")
    plt.ylabel("Count")
    
    [loss, mae] = model.evaluate(data_test, close_prices_test, verbose=0)
    print("Testing set Mean Abs Error: {}".format(mae))

def generate_stock_analysis(csvPath):
    (data, close_prices) = get_stock_data_set(csvPath, 'close')
    data = normalize_features(data)
    data_train, data_test, close_prices_train, close_prices_test = train_test_split_for_stocks(data, close_prices, 60)
    
    model = build_model()
    early_stop = keras.callbacks.EarlyStopping(monitor='val_loss', patience=50)
    EPOCHS = 500
    history = model.fit(data, close_prices, epochs=EPOCHS,
                        validation_data=(data_test, close_prices_test), verbose=0,
                        callbacks=[PrintDot()])
    
    plot_history(history)
    plot_prediction_accuracy_metrics(model, data_test, close_prices_test)
    return model.predict(data_test[0].reshape(1,4))



#prediction = generate_stock_analysis('FB_HistoricalQuotes.csv')
#prediction = generate_stock_analysis('NNDM_HistoricalQuotes.csv')
prediction = generate_stock_analysis('AMZN_HistoricalQuotes.csv')
#prediction = generate_stock_analysis('F_HistoricalQuotes.csv')

#F_data, F_volume = get_stock_data_set('F_HistoricalQuotes.csv', 'volume')
#F_data = normalize_features(F_data)
#F_data_train, F_data_test, F_volume_train, F_volume_test = train_test_split_for_stocks(F_data, F_volume, 60)
#
#model = build_model()
#early_stop = keras.callbacks.EarlyStopping(monitor='val_loss', patience=50)
#EPOCHS = 73
#history = model.fit(F_data_train, F_volume_train, epochs=EPOCHS,
#                    validation_data=(F_data_test, F_volume_test), verbose=0,
#                    callbacks=[early_stop, PrintDot()])
#
#plot_history(history)
#
#F_volume_pred = model.predict(F_data_test)
#F_volume_pred = F_volume_pred.reshape(-1,)
#
#plt.figure()
#plt.scatter(F_volume_test, F_volume_pred)
#plt.xlabel('True Values')
#plt.ylabel('Predictions')
#plt.axis('equal')
#plt.xlim(plt.xlim())
#plt.ylim(plt.ylim())
#plt.plot([-1E9, 1E9], [-1E9, 1E9])
#
#error = F_volume_pred - F_volume_test
#plt.figure()
#plt.hist(error, bins = 30)
#plt.xlabel("Prediction Error")
#plt.ylabel("Count")
#
#[loss, mae] = model.evaluate(F_data_test, F_volume_test, verbose=0)
#print("Testing set Mean Abs Error: {}".format(mae))