import { Component, OnInit, Input } from '@angular/core';
import { FormBuilder, FormGroup, Validators, FormArray } from '@angular/forms';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {

  @Input() system: string;
  @Input() defaultUser: string;
  @Input() parentForm: FormGroup;
  @Input() index: number;
  loginForm: FormGroup;

  constructor(
    private fb: FormBuilder) { }

  ngOnInit() {
    this.loginForm = (this.parentForm.get("logins") as FormArray).controls[this.index] as FormGroup;
  }

}
