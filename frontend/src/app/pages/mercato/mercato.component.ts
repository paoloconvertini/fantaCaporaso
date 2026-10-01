import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MercatoConfigDto } from '../../models/mercato-config.dto';
import {MercatoService} from "../../services/mercato.service";
import { AdminApiService } from '../../services/admin-api.service';

@Component({
    selector: 'app-mercato',
    templateUrl: './mercato.component.html',
    styleUrls: ['./mercato.component.css'],
})
export class MercatoComponent implements OnInit {
    form!: FormGroup;
    loading = false;
    mercatoAttivo = false;
    quotesFile: File | null = null;
    rostersFile: File | null = null;
    quotesResult: any = null;
    rostersResult: any = null;
    quotesLoading = false;
    rostersLoading = false;
    departuresFile: File | null = null;
    departuresResult: any = null;
    departuresLoading = false;
    sessionCode?: string;

    constructor(
        private fb: FormBuilder,
        private service: MercatoService,
        private adminApi: AdminApiService,
        private snackBar: MatSnackBar
    ) {}

    ngOnInit(): void {
        this.form = this.fb.group({
            attiva: [false],
            numeroMercato: [1, [Validators.required, Validators.min(1), Validators.max(3)]],
            maxPortieri: [0, [Validators.required, Validators.min(0)]],
            maxDifensori: [0, [Validators.required, Validators.min(0)]],
            maxCentrocampisti: [0, [Validators.required, Validators.min(0)]],
            maxAttaccanti: [0, [Validators.required, Validators.min(0)]],
            quotazioniAggiornate: [false],
            partitiImportati: [false],
        });

        this.loadConfig();

        // Se cambia lo stato del toggle, abilita/disabilita i campi
        this.form.get('attiva')!.valueChanges.subscribe((val) => {
            this.mercatoAttivo = val;
            if (val) {
                this.form.enable({ emitEvent: false });
            } else {
                // Mantiene il toggle attivo, ma disabilita gli altri campi
                Object.keys(this.form.controls).forEach((key) => {
                    if (key !== 'attiva') {
                        this.form.get(key)!.disable({ emitEvent: false });
                    }
                });
            }
        });
    }

    loadConfig(): void {
        this.loading = true;
        this.service.getConfig().subscribe({
            next: (config) => {
                if (config) {
                    this.resetPreviewsForSession(config.sessionCode);
                    this.form.patchValue(config);
                    this.mercatoAttivo = config.attiva;
                    if (!config.attiva) {
                        Object.keys(this.form.controls).forEach((key) => {
                            if (key !== 'attiva') {
                                this.form.get(key)!.disable({ emitEvent: false });
                            }
                        });
                    }
                } else {
                    // 👇 Nessuna configurazione esistente → inizializza form vuoto
                    this.mercatoAttivo = false;
                    this.form.reset({
                        attiva: false,
                        numeroMercato: 1,
                        maxPortieri: 0,
                        maxDifensori: 0,
                        maxCentrocampisti: 0,
                        maxAttaccanti: 0,
                        quotazioniAggiornate: false,
                        partitiImportati: false,
                    });
                }

                this.loading = false;
            }
        });
    }

    selectQuotesFile(event: Event): void {
        this.quotesFile = (event.target as HTMLInputElement).files?.[0] || null;
        this.quotesResult = null;
    }

    private resetPreviewsForSession(sessionCode?: string): void {
        if (this.sessionCode !== sessionCode) {
            this.departuresResult = this.quotesResult = this.rostersResult = null;
            this.departuresFile = this.quotesFile = this.rostersFile = null;
        }
        this.sessionCode = sessionCode;
    }

    selectDeparturesFile(event: Event): void {
        this.departuresFile = (event.target as HTMLInputElement).files?.[0] || null;
        this.departuresResult = null;
    }

    importDepartures(confirm = false): void {
        if (!this.departuresFile || this.departuresLoading || this.form.get('partitiImportati')?.value) return;
        if (confirm && (!this.departuresResult?.preview || this.departuresResult.errors?.length)) return;
        this.departuresLoading = true;
        this.adminApi.importMarketDepartures(this.departuresFile, confirm).subscribe({
            next: result => {
                this.departuresResult = result;
                this.departuresLoading = false;
                if (confirm && !result.errors?.length) {
                    this.snackBar.open('Partiti importati: ora aggiorna le quotazioni dei restanti', 'Chiudi', { duration: 3500 });
                    this.loadConfig();
                }
            },
            error: error => { this.showError(error); this.departuresLoading = false; }
        });
    }

    previewQuotes(): void {
        if (!this.quotesFile || !this.form.get('partitiImportati')?.value) return;
        this.quotesLoading = true;
        this.adminApi.updateMarketPlayers(this.quotesFile, false).subscribe({
            next: result => { this.quotesResult = result; this.quotesLoading = false; },
            error: error => { this.showError(error); this.quotesLoading = false; }
        });
    }

    confirmQuotes(): void {
        if (!this.quotesFile || !this.quotesResult?.preview || !this.form.get('partitiImportati')?.value) return;
        this.quotesLoading = true;
        this.adminApi.updateMarketPlayers(this.quotesFile, true).subscribe({
            next: result => {
                this.quotesResult = result;
                this.quotesLoading = false;
                this.snackBar.open('Quotazioni aggiornate: ora puoi gestire le cessioni', 'Chiudi', { duration: 3500 });
                this.loadConfig();
            },
            error: error => { this.showError(error); this.quotesLoading = false; }
        });
    }

    editMissingValue(player: any): void {
        if (!player?.playerId) return;
        const raw = window.prompt(`Quotazione attuale di ${player.name}`, String(player.oldValue ?? 0));
        if (raw === null) return;
        const value = Number(raw.replace(',', '.'));
        if (!Number.isFinite(value) || value < 0) {
            this.snackBar.open('Quotazione non valida', 'Chiudi', { duration: 2500 });
            return;
        }
        this.adminApi.updatePlayerValue(player.playerId, value).subscribe({
            next: () => {
                player.oldValue = value;
                this.snackBar.open('Quotazione aggiornata', 'Chiudi', { duration: 2000 });
            },
            error: error => this.showError(error)
        });
    }

    selectRostersFile(event: Event): void {
        this.rostersFile = (event.target as HTMLInputElement).files?.[0] || null;
        this.rostersResult = null;
    }

    previewRosters(): void {
        if (!this.rostersFile) return;
        this.rostersLoading = true;
        this.adminApi.reconcileMarketRosters(this.rostersFile, false).subscribe({
            next: result => { this.rostersResult = result; this.rostersLoading = false; },
            error: error => { this.showError(error); this.rostersLoading = false; }
        });
    }

    confirmRosters(): void {
        if (!this.rostersFile || !this.rostersResult?.preview || this.rostersResult?.errors?.length) return;
        if (!window.confirm('Applicare correzioni delle riserve portieri, scambi e cessioni mostrati nell’anteprima?')) return;
        this.rostersLoading = true;
        this.adminApi.reconcileMarketRosters(this.rostersFile, true).subscribe({
            next: result => {
                this.rostersResult = result;
                this.rostersLoading = false;
                this.snackBar.open('Rose riconciliate', 'Chiudi', { duration: 2500 });
            },
            error: error => { this.showError(error); this.rostersLoading = false; }
        });
    }

    private showError(error: any): void {
        this.snackBar.open(error?.error?.error || 'Operazione non riuscita', 'Chiudi', { duration: 4000 });
    }


    salva(): void {
        if (this.form.invalid) {
            this.snackBar.open('Compila correttamente tutti i campi', 'Chiudi', { duration: 3000 });
            return;
        }

        const dto: MercatoConfigDto = this.form.getRawValue(); // include anche i campi disabilitati

        this.service.updateConfig(dto).subscribe({
            next: (saved) => {
                this.resetPreviewsForSession(saved.sessionCode);
                this.form.patchValue(saved);
                this.snackBar.open('Configurazione aggiornata', 'Chiudi', { duration: 2000 });
            }
        });
    }
}
