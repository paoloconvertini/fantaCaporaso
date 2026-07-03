export const environment = {
    production: true,
    useHashRouting: false,
    keycloak: {
        url: `${window.location.origin}/auth`,
        realm: 'fantasta',
        clientId: 'fantasta-frontend'
    }
};
