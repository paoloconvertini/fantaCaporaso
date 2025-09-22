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
  adminPin = '1234';

  constructor(private api: AdminApiService) {}

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
  }

  uploadFile() {
    if (!this.selectedFile) return;

    this.loading = true;
    this.api.uploadRosterExcel(this.selectedFile).subscribe({
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
