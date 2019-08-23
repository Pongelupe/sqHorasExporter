import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Observable } from '../../node_modules/rxjs';

@Injectable({
  providedIn: 'root'
})
export class AppService {

  constructor(private http: HttpClient) { }

  send(formValue: { generateExcel: boolean, logins: [{ username: string, password: string }] }): Observable<HttpResponse<Blob>> {
    const body = {
      generateExcel: formValue.generateExcel, loginInfoSqhoras: {
        username: formValue.logins[0].username,
        password: formValue.logins[0].password
      }, loginInfoRMPortal: {
        username: formValue.logins[1].username,
        password: formValue.logins[1].password
      }, month: new Date()
    };

    return this.http.post<Blob>('/api/squadra', body, {
      responseType: 'blob' as 'json',
      observe: 'response'
    });
  }

}
