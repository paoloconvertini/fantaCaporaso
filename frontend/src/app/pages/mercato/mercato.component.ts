import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MercatoConfigDto } from '../../models/mercato-config.dto';
import {MercatoService} from "../../services/mercato.service";

@Component({
    selector: 'app-mercato',
    templateUrl: './mercato.component.html',
})
export class MercatoComponent implements OnInit {
    form!: FormGroup;
    loading = false;
    mercatoAttivo = false;

    constructor(
        private fb: FormBuilder,
        private service: MercatoService,
        private snackBar: MatSnackBar
    ) {}

    ngOnInit(): void {
        this.form = this.fb.group({
            attiva: [false],
            fineSessione: ['', Validators.required],
            maxPortieri: [0, [Validators.required, Validators.min(0)]],
            maxDifensori: [0, [Validators.required, Validators.min(0)]],
            maxCentrocampisti: [0, [Validators.required, Validators.min(0)]],
            maxAttaccanti: [0, [Validators.required, Validators.min(0)]],
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
                        fineSessione: '',
                        maxPortieri: 0,
                        maxDifensori: 0,
                        maxCentrocampisti: 0,
                        maxAttaccanti: 0,
                    });
                }

                this.loading = false;
            }
        });
    }


    salva(): void {
        if (this.form.invalid) {
            this.snackBar.open('Compila correttamente tutti i campi', 'Chiudi', { duration: 3000 });
            return;
        }

        const dto: MercatoConfigDto = this.form.getRawValue(); // include anche i campi disabilitati

        this.service.updateConfig(dto).subscribe({
            next: () => {
                this.snackBar.open('Configurazione aggiornata', 'Chiudi', { duration: 2000 });
            }
        });
    }
}
