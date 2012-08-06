import logging

def get_logger(log_name, log_handler, log_level):
    logger = logging.getLogger(log_name)
    logger.addHandler(log_handler)
    logger.setLevel(log_level)

    return logger
