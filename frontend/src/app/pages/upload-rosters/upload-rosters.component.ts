import { Component } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-upload-rosters',
  templateUrl: './upload-rosters.component.html',
  styleUrls: ['./upload-rosters.component.css']
})
export class UploadRostersComponent {
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

    this.http.post(`${this.API_BASE}/admin/rosters/upload?pin=1234`, formData)
        .subscribe({
          next: res => this.uploadResult = res,
          error: err => this.uploadResult = { error: err.message }
        });
  }
}

