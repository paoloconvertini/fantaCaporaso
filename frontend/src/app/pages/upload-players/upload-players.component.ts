import { Component } from '@angular/core';
import {AdminApiService} from "../../services/admin-api.service";

@Component({
  selector: 'app-upload-players',
  templateUrl: './upload-players.component.html',
  styleUrls: ['./upload-players.component.css']
})
export class UploadPlayersComponent {
  selectedFile: File | null = null;
  uploadResult: any = null;
  loading = false;

  constructor(private api: AdminApiService) {}

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
    this.uploadResult = null;
  }

  uploadFile() {
    if (!this.selectedFile) return;

    this.loading = true;
    this.api.uploadPlayersExcel(this.selectedFile, false).subscribe({
      next: res => {
        this.uploadResult = res;
        this.loading = false;
      },
      error: err => {
        this.uploadResult = { error: err?.error?.error || 'File non valido' };
        this.loading = false;
      }
    });
  }

  confirmImport() {
    if (!this.selectedFile || !this.uploadResult?.preview) return;
    if (!window.confirm(`Sostituire il catalogo con ${this.uploadResult.total} calciatori? Rose e stato asta saranno azzerati.`)) return;

    this.loading = true;
    this.api.uploadPlayersExcel(this.selectedFile, true).subscribe({
      next: res => {
        this.uploadResult = res;
        this.loading = false;
      },
      error: err => {
        this.uploadResult = { error: err?.error?.error || 'Importazione non riuscita' };
        this.loading = false;
      }
    });
  }

}
