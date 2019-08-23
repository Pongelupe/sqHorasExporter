import { Component, OnInit } from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { AppService } from './app.service';


@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit {

  parentForm: FormGroup;
  processing = false;
  progressValue = 0;
  interval: any;

  constructor(private fb: FormBuilder, private service: AppService) { }

  ngOnInit() {
    this.parentForm = this.fb.group({
      generateExcel: [true, [Validators.required]],
      logins: this.fb.array([
        this.fb.group({
          username: ['', [Validators.required]],
          password: ['', [Validators.required]]
        }),
        this.fb.group({
          username: ['', [Validators.required]],
          password: ['', [Validators.required]]
        })
      ])
    });
  }

  send() {
    this.processing = true;
    this.startTimer();
    this.service.send(this.parentForm.value)
      .pipe(
        finalize(() => {
          this.processing = false;
          clearInterval(this.interval);
          this.progressValue = 0;
        }),
    )
      .subscribe(response => {
        const blob = new Blob([response.body], {
          type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        });
        const downloadURL = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = downloadURL;
        const contentDispositionHeader = response.headers.get('content-disposition');
        link.download = contentDispositionHeader.substr(contentDispositionHeader.indexOf('=') + 1);
        link.click();
      },
        (err) => console.log(err))
  }

  private startTimer() {

    this.interval = setInterval(() => {
      this.progressValue = this.progressValue == 100 ? 1 : this.progressValue + 1;
    }, 1000);
  }

}
