package webapp.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import webapp.controllers.StocksController;

@Service
public class NERModelTrainingService {
    final static Logger logger = LogManager.getLogger(NERModelTrainingService.class);

    private static boolean trainingInProgress = false;

    @Async("processExecutor")
    public void process(StocksController stocksController) {
        if (!trainingInProgress) {
            trainingInProgress = true;
            logger.info("Begin model training.");
            stocksController.initiateNERModelTraining();
            logger.info("Model training complete.");
            trainingInProgress = false;
        }
    }
}
