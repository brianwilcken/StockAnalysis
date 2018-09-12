# -*- coding: utf-8 -*-
"""
Created on Wed Sep 12 10:02:49 2018

@author: WILCBM
"""

import tensorflow as tf
from tensorflow import keras
from sklearn.model_selection import train_test_split
import numpy as np
import plot_tools as plt
import common as com

stock_data = com.pull_stock_data('AAPL', '1min')



#plt.plot_stock_activity(stock_data, 'AAPL 1 Minute Data', True)

#set aside 20% of data as a blind test
stock_data, blind_test_data = com.modify_for_blind_test(stock_data, 0.2)

#extract training labels based on a specific data column
data, labels = com.extract_labels(stock_data, 'Close')
blind_data, blind_labels = com.extract_labels(blind_test_data, 'Close')

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

#build and compile the model
model = keras.Sequential([
        keras.layers.Dense(16, activation=tf.nn.relu, kernel_regularizer=keras.regularizers.l1(0.001)),
        #keras.layers.Dropout(0.03),
        keras.layers.Dense(16, activation=tf.nn.relu),
        #keras.layers.Dropout(0.03),
        #keras.layers.Dense(1024, activation=tf.nn.relu),
        #keras.layers.Dense(96, activation=tf.nn.relu),
        keras.layers.Dense(1)
      ])
    
optimizer = tf.train.RMSPropOptimizer(0.001)

model.compile(loss=tf.losses.mean_squared_error,
            optimizer=optimizer,
            metrics=['mae'])

#fit the training data to the model
#early_stop = keras.callbacks.EarlyStopping(monitor='val_loss', patience=50)
EPOCHS = 150
with tf.Session(config=tf.ConfigProto(log_device_placement=True, device_count={'CPU' : 1, 'GPU' : 0})) as sess:
    sess.run(tf.global_variables_initializer())
    history = model.fit(data_train, labels_train, epochs=EPOCHS,
                        validation_data=(data_test, labels_test), verbose=1)
    
    #plot model performance data
    plt.plot_history(history)
    plt.plot_prediction_accuracy_metrics(model, blind_data, blind_labels)
    predicted = plt.plot_true_versus_predicted_activity(model, blind_data, blind_labels, 'Close')
