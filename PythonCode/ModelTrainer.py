# -*- coding: utf-8 -*-
"""
Created on Sat Sep 29 13:46:28 2018

@author: Brian
"""

import sys
import getopt
import common as com
import tensorflow as tf
import tf_keras_models as mod
from tensorflow import keras

def main(argv):
    
    try:
        opts, args = getopt.getopt(argv, 's:i:', ['symbol=', 'interval='])
    except getopt.GetoptError:
        print('ModelTrainer.py -s <symbol> -i <interval>')
        sys.exit(2)

    if len(opts) > 0:
        for opt, arg in opts:
            if opt in ('-s', 'symbol'):
                symbol = arg
            elif opt in ('-i', 'interval'):
                interval = arg
    else:
        symbol = 'AMRN'
        interval = 'Daily'
    
    data_train, data_val, labels_train, labels_val = com.retrieve_model_training_data(symbol, interval, 'Close')
    
    train_model(symbol, interval, data_train, data_val, labels_train, labels_val)
    
    com.save_prediction_accuracy_metrics(symbol, interval)
    

def train_model(symbol, interval, data_train, data_val, labels_train, labels_val):
    with tf.Session(config=tf.ConfigProto(log_device_placement=True, device_count={'CPU' : 1, 'GPU' : 0})) as sess:
        sess.run(tf.global_variables_initializer())
        
        #build and compile the model
        if symbol in mod.modelFuncs and interval in mod.modelFuncs[symbol]:
            model, epochs, early_stop_patience = mod.modelFuncs[symbol][interval]()
        else:
            model, epochs, early_stop_patience = mod.get_generic_model()
        
        early_stop = keras.callbacks.EarlyStopping(monitor='val_loss', patience=early_stop_patience)
        model.fit(data_train, labels_train, epochs=epochs, 
                  validation_data=(data_val, labels_val), verbose=1, callbacks=[early_stop])
        
        #save the model for later use
        model.save(com.get_model_file_path(symbol, interval))

    
if __name__ == "__main__":
   main(sys.argv[1:])
