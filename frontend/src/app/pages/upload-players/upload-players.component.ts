import { Component } from '@angular/core';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-upload-players',
  templateUrl: './upload-players.component.html',
  styleUrls: ['./upload-players.component.css']
})
export class UploadPlayersComponent {
  selectedFile: File | null = null;
  uploadResult: any = null;
  loading = false;
  adminPin = '1234';

  constructor(private api: ApiService) {}

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
  }

  uploadFile() {
    if (!this.selectedFile) return;

    this.loading = true;
    this.api.uploadPlayersExcel(this.selectedFile, this.adminPin).subscribe({
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