export interface MercatoConfigDto {
    attiva: boolean;
    /** Campo legacy mantenuto per compatibilità; l'apertura è controllata da attiva. */
    fineSessione?: string;
    maxPortieri: number;
    maxDifensori: number;
    maxCentrocampisti: number;
    maxAttaccanti: number;
    numeroMercato: number;
    sessionCode?: string;
    quotazioniAggiornate: boolean;
    quotazioniAggiornateAt?: string;
}
