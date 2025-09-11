import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-upload-players',
  templateUrl: './upload-players.component.html',
  styleUrls: ['./upload-players.component.css']
})
export class UploadPlayersComponent {
  selectedFile: File | null = null;
  uploadResult: any = null;
  readonly API_BASE = (window as any)['API_BASE'] || 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
  }

  uploadFile() {
    if (!this.selectedFile) return;

    const formData = new FormData();
    formData.append('file', this.selectedFile);

    // 🔹 PIN admin hardcoded (puoi migliorare con login/sessione)
    this.http.post(`${this.API_BASE}/admin/players/upload?pin=1234`, formData)
        .subscribe({
          next: res => this.uploadResult = res,
          error: err => this.uploadResult = { error: err.message }
        });
  }
}
