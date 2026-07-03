const keycloakUrl = `${window.location.protocol}//${window.location.hostname}:8081`;

export const environment = {
    production: false,
    useHashRouting: true,
    keycloak: {
        url: keycloakUrl,
        realm: 'fantasta',
        clientId: 'fantasta-frontend'
    }
};
