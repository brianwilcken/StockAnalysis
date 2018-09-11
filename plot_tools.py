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
    
    [loss, mae] = model.evaluate(data_test, labels_test, verbose=0)
    print("Testing set Mean Abs Error: {}".format(mae))
    
def plot_stock_activity(data, attribute):
    plt.figure()
    plt.plot(data[attribute])
    plt.xlabel('Interval')
    plt.ylabel(attribute)
    
def plot_true_versus_predicted_activity(model, data, true_values, attribute):
    pred_values = model.predict(data)
    
    plt.figure()
    plt.plot(true_values, label='True Values')
    plt.plot(pred_values, label='Predictions')
    plt.plot(true_values - pred_values.reshape(-1), label='Difference')
    plt.plot([0, len(true_values)], [0, 0], label='Baseline')
    plt.xlabel('Interval')
    plt.ylabel(attribute)
    plt.legend()
    
    return pred_values