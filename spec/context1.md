Esse arquivo corresponde as alterações que eu quero que você faça no back-end e no front-end
1. apague as tabelas jhi_authority e jhi_user_authority e também apague tudo relacionado a autoridade de usuários no java (não vou mais usar isso)
2. mantenha só as colunas, id, password_hash, email, activated, activation_key, reset_key, created_by, created_date e reset_date. De resto, apague tudo
3. adicione uma coluna name, que corresponde ao nome do usuário, e outra coluna description (ambas as colunas são varchar(255) e name é obrigatório, description não é)
4. a coluna name deve ter no mínimo 4 caracteres
5. o login agora não usará mais a coluna login pois essa será apagada. O login vai usar o email. Eu quero que você apague essa coluna login no banco e passe a fazer a autenticação usando a coluna email. Faça as alterações no front-end e no back-end para que isso seja possível