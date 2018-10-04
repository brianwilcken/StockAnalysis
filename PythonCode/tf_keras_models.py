import tensorflow as tf
from tensorflow import keras

def get_AAPL_1min_Model():
    model = keras.Sequential([
        keras.layers.Dense(4, activation=tf.nn.relu, kernel_regularizer=keras.regularizers.l1(0.001)),
#        keras.layers.Dropout(0.03),
#        keras.layers.Dense(64, activation=tf.nn.relu),
#        keras.layers.Dropout(0.03),
        #keras.layers.Dense(1024, activation=tf.nn.relu),
        #keras.layers.Dense(96, activation=tf.nn.relu),
        keras.layers.Dense(1)
      ])
    
    compile_model(model)
    
    epochs = 1000
    early_stop_patience = 50
    return model, epochs, early_stop_patience

def get_AAPL_Daily_Model():
    model = keras.Sequential([
        keras.layers.Dense(16, activation=tf.nn.relu, kernel_regularizer=keras.regularizers.l1(0.001)),
        #keras.layers.Dropout(0.03),
        keras.layers.Dense(16, activation=tf.nn.relu),
        #keras.layers.Dropout(0.03),
        #keras.layers.Dense(1024, activation=tf.nn.relu),
        #keras.layers.Dense(96, activation=tf.nn.relu),
        keras.layers.Dense(1)
      ])
    
    compile_model(model)
    
    epochs = 300
    early_stop_patience = 50
    return model, epochs, early_stop_patience

def get_FB_1min_Model():
    model = keras.Sequential([
        keras.layers.Dense(16, activation=tf.nn.relu, kernel_regularizer=keras.regularizers.l1(0.001)),
        #keras.layers.Dropout(0.03),
        keras.layers.Dense(16, activation=tf.nn.relu),
        #keras.layers.Dropout(0.03),
        #keras.layers.Dense(1024, activation=tf.nn.relu),
        #keras.layers.Dense(96, activation=tf.nn.relu),
        keras.layers.Dense(1)
      ])
    
    compile_model(model)
    
    epochs = 300
    early_stop_patience = 50
    return model, epochs, early_stop_patience

def get_MSFT_1min_Model():
    model = keras.Sequential([
        keras.layers.Dense(4, activation=tf.nn.relu, kernel_regularizer=keras.regularizers.l1(0.001)),
#        keras.layers.Dropout(0.03),
#        keras.layers.Dense(64, activation=tf.nn.relu),
#        keras.layers.Dropout(0.03),
        #keras.layers.Dense(1024, activation=tf.nn.relu),
        #keras.layers.Dense(96, activation=tf.nn.relu),
        keras.layers.Dense(1)
      ])
    
    compile_model(model)
    
    epochs = 1000
    early_stop_patience = 50
    return model, epochs, early_stop_patience

def get_MSFT_Daily_Model():
    model = keras.Sequential([
        keras.layers.Dense(16, activation=tf.nn.relu, kernel_regularizer=keras.regularizers.l1(0.001)),
        #keras.layers.Dropout(0.03),
        keras.layers.Dense(16, activation=tf.nn.relu),
        #keras.layers.Dropout(0.03),
        #keras.layers.Dense(1024, activation=tf.nn.relu),
        #keras.layers.Dense(96, activation=tf.nn.relu),
        keras.layers.Dense(1)
      ])
    
    compile_model(model)
    
    epochs = 300
    early_stop_patience = 50
    return model, epochs, early_stop_patience

def get_NFLX_1min_Model():
    model = keras.Sequential([
        keras.layers.Dense(4, activation=tf.nn.relu, kernel_regularizer=keras.regularizers.l1(0.001)),
#        keras.layers.Dropout(0.03),
#        keras.layers.Dense(64, activation=tf.nn.relu),
#        keras.layers.Dropout(0.03),
        #keras.layers.Dense(1024, activation=tf.nn.relu),
        #keras.layers.Dense(96, activation=tf.nn.relu),
        keras.layers.Dense(1)
      ])
    
    compile_model(model)
    
    epochs = 1000
    early_stop_patience = 50
    return model, epochs, early_stop_patience

def get_NFLX_Daily_Model():
    model = keras.Sequential([
        keras.layers.Dense(4, activation=tf.nn.relu, kernel_regularizer=keras.regularizers.l1(0.001)),
        keras.layers.Dense(1)
      ])
    
    compile_model(model)
    
    epochs = 1000
    early_stop_patience = 20
    return model, epochs, early_stop_patience

def get_VKTX_1min_Model():
    model = keras.Sequential([
        keras.layers.Dense(16, activation=tf.nn.relu, kernel_regularizer=keras.regularizers.l1(0.001)),
#        keras.layers.Dropout(0.03),
#        keras.layers.Dense(64, activation=tf.nn.relu),
#        keras.layers.Dropout(0.03),
        #keras.layers.Dense(1024, activation=tf.nn.relu),
        #keras.layers.Dense(96, activation=tf.nn.relu),
        keras.layers.Dense(1)
      ])
    
    compile_model(model)
    
    epochs = 1000
    early_stop_patience = 50
    return model, epochs, early_stop_patience

def get_VKTX_Daily_Model():
    model = keras.Sequential([
        keras.layers.Dense(16, activation=tf.nn.relu, kernel_regularizer=keras.regularizers.l1(0.001)),
        keras.layers.Dense(1)
      ])
    
    compile_model(model)
    
    epochs = 1000
    early_stop_patience = 30
    return model, epochs, early_stop_patience

def get_AMRN_Daily_Model():
    model = keras.Sequential([
        keras.layers.Dense(96, activation=tf.nn.relu, kernel_regularizer=keras.regularizers.l1(0.001)),
        keras.layers.Dropout(0.03),
        keras.layers.Dense(96, activation=tf.nn.relu, kernel_regularizer=keras.regularizers.l1(0.001)),
        keras.layers.Dense(1)
      ])
    
    compile_model(model)
    
    epochs = 1000
    early_stop_patience = 30
    return model, epochs, early_stop_patience

def compile_model(model):
    optimizer = tf.train.RMSPropOptimizer(0.001)
    
    model.compile(loss=tf.losses.mean_squared_error,
                optimizer=optimizer,
                metrics=['mae'])

modelFuncs = {
        'AAPL': {
            '1min': get_AAPL_1min_Model,
            'Daily': get_AAPL_Daily_Model
        },
        'FB': { 
            '1min': get_FB_1min_Model
        },
        'MSFT': {
            '1min': get_MSFT_1min_Model,
            'Daily': get_MSFT_Daily_Model
        },
        'NFLX': {
            '1min': get_NFLX_1min_Model,
            'Daily': get_NFLX_Daily_Model
        },
        'VKTX': {
            '1min': get_VKTX_1min_Model,
            'Daily': get_VKTX_Daily_Model
        },
        'AMRN': {
            'Daily': get_AMRN_Daily_Model
        },
    }
        
def get_generic_model():
    model = keras.Sequential([
        keras.layers.Dense(16, activation=tf.nn.relu, kernel_regularizer=keras.regularizers.l1(0.001)),
        keras.layers.Dense(1)
      ])
    
    compile_model(model)
    
    epochs = 1000
    early_stop_patience = 20
    return model, epochs, early_stop_patience