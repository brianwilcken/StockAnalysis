# -*- coding: utf-8 -*-
"""
Created on Fri Sep 28 11:11:16 2018

@author: bmwin
"""
import os

os.chdir('\\\\Desktop-ltmq880\\c\\code\\StockAnalysis\\PythonCode')

import numpy as np
from tensorflow import keras
import pickle

symbol = 'AMRN'
interval = 'Daily'

model_file_path = './models/' + symbol + '_' + interval + '/model.h5'
norm_file_path = './norm_factors/' + symbol + '_' + interval + '/norm.pkl'
perf_file_path = './perf_metrics/' + symbol + '_' + interval + '/perf.pkl'

model = keras.models.load_model(model_file_path)

with open(norm_file_path, 'rb') as f:
    mean, std = pickle.load(f)
    
with open(perf_file_path, 'rb') as f:
    mean_error, mae = pickle.load(f)

quote = np.array([[18.28, 15.2, 16.21, 32000000]])
quote_norm = (quote - mean) / std

print('Predicted closing price: ${0}'.format(model.predict(quote_norm)))

