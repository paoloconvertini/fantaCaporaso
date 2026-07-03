export interface MercatoConfigDto {
    attiva: boolean;
    fineSessione: string; // ISO string (Instant)
    maxPortieri: number;
    maxDifensori: number;
    maxCentrocampisti: number;
    maxAttaccanti: number;
}
