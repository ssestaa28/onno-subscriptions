# Развёртывание на VPS

Подготовленные конфигурации рассчитаны на новый сервер Ubuntu 24.04:
2 ГБ RAM, 1–2 vCPU, 20 ГБ диска, публичный IPv4. Приложение собирается
на локальном компьютере; на сервер передаётся готовый JAR.

## Подготовка сервера

Команды в SSH-консоли сервера от root:

```bash
apt update
apt install -y openjdk-21-jre-headless nginx
useradd --system --home-dir /var/lib/onno-subscriptions --shell /usr/sbin/nologin onno
install -d -m 0755 /opt/onno-subscriptions
```

## Передача файлов

В локальном PowerShell из папки проекта, заменив SERVER_IP адресом сервера:

```powershell
.\gradlew.bat --gradle-user-home .gradle-home bootJar
scp build/libs/onno-subscriptions-1.0.0.jar root@SERVER_IP:/opt/onno-subscriptions/app.jar
scp deploy/onno-subscriptions.service root@SERVER_IP:/etc/systemd/system/onno-subscriptions.service
scp deploy/nginx.conf root@SERVER_IP:/etc/nginx/sites-available/onno-subscriptions
```

## Конфигурация и запуск

В SSH-консоли сервера:

```bash
install -m 0600 /dev/null /etc/onno-subscriptions.env
nano /etc/onno-subscriptions.env
```

Введите параметры; замените значение пароля собственным уникальным паролем
(для простоты используйте латинские буквы и цифры):

```text
APP_ADMIN_PASSWORD=REPLACE_WITH_UNIQUE_PASSWORD
SERVER_ADDRESS=127.0.0.1
SERVER_FORWARD_HEADERS_STRATEGY=framework
PORT=8080
```

Сохраните файл. Затем:

```bash
ln -s /etc/nginx/sites-available/onno-subscriptions /etc/nginx/sites-enabled/onno-subscriptions
```

На новом сервере отключите только стандартную конфигурацию nginx:

```bash
unlink /etc/nginx/sites-enabled/default
nginx -t
systemctl daemon-reload
systemctl enable --now onno-subscriptions
systemctl reload nginx
systemctl status onno-subscriptions --no-pager
```

В сетевом экране провайдера разрешите входящие TCP 22, 80 и 443.
Порт приложения 8080 слушает только localhost.

Первичная проверка: `http://SERVER_IP/ui/`, пользователь `admin`, пароль из
файла окружения. До передачи доступа проверяющему подключите домен и HTTPS:
HTTP используется только для первоначальной диагностики.

## Данные и обслуживание

Это отдельная установка с пустой базой: локальные тестовые записи не передаются.
H2 хранится в `/var/lib/onno-subscriptions/data/subscriptions.mv.db`;
перезапуск сервиса данные сохраняет. Перед резервным копированием файла базы
остановите сервис, после копирования запустите снова.

```bash
journalctl -u onno-subscriptions -n 100 --no-pager
systemctl restart onno-subscriptions
```

Для обновления остановите сервис, передайте новый `app.jar`, запустите сервис.
Конфигурации подготовлены локально; развёртывание и проверка на конкретном
VPS должны выполняться отдельно.
