import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {UserApiService} from "../services/user-api.service";

@Component({
    selector: 'app-manual-assign-dialog',
    templateUrl: './manual-assign-dialog.component.html',
    styleUrls: ['./manual-assign-dialog.component.css']
})
export class ManualAssignDialogComponent {
    participants: any[] = [];
    selectedParticipantId: number | null = null;
    amount: number;

    constructor(
        private api: UserApiService,
        private dialogRef: MatDialogRef<ManualAssignDialogComponent>,
        @Inject(MAT_DIALOG_DATA) public data: any
    ) {
        this.amount = data?.role === 'PORTIERE' ? 3 : 1;
        this.api.getParticipants().subscribe(res => this.participants = res);
    }

    get minimumAmount(): number {
        return this.data?.role === 'PORTIERE' ? 3 : 1;
    }

    save() {
        if (!this.selectedParticipantId || this.amount < this.minimumAmount) return;
        this.dialogRef.close({ participantId: this.selectedParticipantId, amount: this.amount });
    }

    cancel() {
        this.dialogRef.close();
    }
}
