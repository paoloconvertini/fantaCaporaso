import { Component } from '@angular/core';
import {AdminApiService} from "../../services/admin-api.service";

@Component({
  selector: 'app-upload-rosters',
  templateUrl: './upload-rosters.component.html',
  styleUrls: ['./upload-rosters.component.css']
})
export class UploadRostersComponent {
  selectedFile: File | null = null;
  uploadResult: any = null;
  loading = false;
  constructor(private api: AdminApiService) {}

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
  }

  uploadFile() {
    if (!this.selectedFile) return;

    this.loading = true;
    this.api.uploadRosterExcel(this.selectedFile, false).subscribe({
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
    if (!window.confirm(`Importare ${this.uploadResult.teamsFound} squadre e ${this.uploadResult.inserted} calciatori?`)) return;
    this.loading = true;
    this.api.uploadRosterExcel(this.selectedFile, true).subscribe({
      next: res => { this.uploadResult = res; this.loading = false; },
      error: err => { this.uploadResult = { error: err?.error?.error || 'Importazione non riuscita' }; this.loading = false; }
    });
  }
}
