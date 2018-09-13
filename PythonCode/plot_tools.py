# -*- coding: utf-8 -*-
"""
Created on Mon Sep 10 12:12:01 2018

@author: WILCBM
"""
import numpy as np
import matplotlib.pyplot as plt

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
  
def plot_prediction_accuracy_metrics(model, data_test, labels_test):
    labels_pred = model.predict(data_test)
    labels_pred = labels_pred.reshape(-1,)
    
    plt.figure()
    plt.scatter(labels_test, labels_pred)
    plt.xlabel('True Values')
    plt.ylabel('Predictions')
    plt.axis('equal')
    plt.xlim(plt.xlim())
    plt.ylim(plt.ylim())
    plt.plot([-5000, 5000], [-5000, 5000])
    
    error = labels_pred - labels_test
    plt.figure()
    plt.hist(error, bins = 30)
    plt.xlabel("Prediction Error")
    plt.ylabel("Count")
    
    return error
    
def plot_stock_activity(data, title=None, flip=False):
    open_data = data['Open'].values
    high_data = data['High'].values
    low_data = data['Low'].values
    close_data = data['Close'].values
    volume_data = data['Volume'].values
    if flip:
        open_data = open_data[::-1]
        high_data = high_data[::-1]
        low_data = low_data[::-1]
        close_data = close_data[::-1]
        volume_data = volume_data[::-1]
        
    fig = plt.figure()
    
    ax1 = fig.add_subplot(111)
    volume_plot = ax1.plot(volume_data)
    ax1.set_ylabel('Volume Shares')
    
    ax2 = fig.add_subplot(111, sharex=ax1, frameon=False)
    open_plot = ax2.plot(open_data, 'bo', label='Open', markersize=2)
    high_plot = ax2.plot(high_data, 'g_', label='High', markersize=5)
    low_plot = ax2.plot(low_data, 'r_', label='Low', markersize=5)
    close_plot = ax2.plot(close_data, 'k-', label='Close', markersize=2)
    ax2.yaxis.tick_right()
    ax2.yaxis.set_label_position('right')
    ax2.set_ylabel('Price $')
    if title is not None:
        ax2.set_title(title)
    ax2.legend()

    fig.show()
    
def plot_true_versus_predicted_activity(model, data, true_values, attribute):
    pred_values = model.predict(data)
    
    plt.figure()
    plt.plot(true_values, 'bo', markersize=4, label='True Values')
    plt.plot(pred_values, 'ro', markersize=4, label='Predictions')
    plt.plot(true_values - pred_values.reshape(-1), label='Difference')
    plt.plot([0, len(true_values)], [0, 0], label='Baseline')
    plt.xlabel('Interval')
    plt.ylabel(attribute)
    plt.legend()
    
    return pred_values