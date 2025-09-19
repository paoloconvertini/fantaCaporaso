<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <title>FantaCaporaso - Login</title>
    <link rel="stylesheet" href="${url.resourcesPath}/css/login.css" />
</head>
<body>
<div class="home-container">
    <div class="kc-card">
        <h1>FantaCaporaso</h1>
        <p class="subtitle">Gestisci la tua asta di Fantacalcio</p>

        <form id="kc-form-login" action="${url.loginAction}" method="post">
            <div class="kc-input">
                <label for="username">Utente</label>
                <input type="text" id="username" name="username" value="${username!}" autofocus autocomplete="username">
            </div>

            <div class="kc-input">
                <label for="password">Password</label>
                <input type="password" id="password" name="password" autocomplete="current-password">
            </div>

            <div class="kc-actions">
                <input type="submit" value="Entra" class="kc-button">
            </div>
        </form>
    </div>
</div>
</body>
</html>
