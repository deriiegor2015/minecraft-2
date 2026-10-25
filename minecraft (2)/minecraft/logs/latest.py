import logging
import sys

# Автоматичне налаштування логування для всього проєкту
logging.basicConfig(
    level=logging.DEBUG,  # Логувати абсолютно всі події (від простих повідомлень до помилок)
    format="[%(asctime)s] [%(levelname)s] (рядок %(lineno)d): %(message)s",
    datefmt="%H:%M:%S",
    handlers=[
        logging.FileHandler("latest.log", encoding="utf-8", mode="w"), # mode="w" означає, що при кожному запуску файл оновлюється
        logging.StreamHandler(sys.stdout)                             # дублює все у віконце командного рядка
    ]
)

logging.info("=== СИСТЕМА ЛОГУВАННЯ ЗАПУЩЕНА АВТОМАТИЧНО ===")