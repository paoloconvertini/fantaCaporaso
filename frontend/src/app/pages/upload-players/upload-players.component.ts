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
  }

  uploadFile() {
    if (!this.selectedFile) return;

    this.loading = true;
    this.api.uploadPlayersExcel(this.selectedFile).subscribe({
      next: res => {
        this.uploadResult = res;
        this.loading = false;
      },
      error: err => {
        console.error(err);
        this.uploadResult = { error: err.message || 'Errore upload' };
        this.loading = false;
      }
    });
  }

}