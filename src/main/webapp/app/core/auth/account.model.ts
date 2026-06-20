export class Account {
  constructor(public email: string, public name: string | null, public description: string | null, public activated?: boolean) {}
}
