import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {ApiService} from "../services/api.service";

@Component({
    selector: 'app-manual-assign-dialog',
    templateUrl: './manual-assign-dialog.component.html',
    styleUrls: ['./manual-assign-dialog.component.css']
})
export class ManualAssignDialogComponent {
    participants: any[] = [];
    selectedParticipantId: number | null = null;
    amount: number = 0;

    constructor(
        private api: ApiService,
        private dialogRef: MatDialogRef<ManualAssignDialogComponent>,
        @Inject(MAT_DIALOG_DATA) public data: any
    ) {
        this.api.getParticipants().subscribe(res => this.participants = res);
    }

    save() {
        if (!this.selectedParticipantId || this.amount <= 0) return;
        this.dialogRef.close({ participantId: this.selectedParticipantId, amount: this.amount });
    }

    cancel() {
        this.dialogRef.close();
    }
}
