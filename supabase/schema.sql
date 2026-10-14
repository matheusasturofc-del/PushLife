-- Banco do PushLife no Supabase. Cole tudo no SQL Editor e clique em "Run" (uma vez só).

-- Um perfil por conta: o @ (único), o nome e o backup do app.
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  username text not null unique check (username ~ '^[a-z][a-z0-9_.]{2,19}$'),
  name text not null default '',
  backup text,
  backup_at timestamptz,
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

-- Cada pessoa só vê e só muda o próprio perfil.
drop policy if exists "dono le" on public.profiles;
create policy "dono le" on public.profiles for select using (auth.uid() = id);
drop policy if exists "dono atualiza" on public.profiles;
create policy "dono atualiza" on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id);

-- Qualquer um pode perguntar se um @ está livre, sem ver de quem é.
create or replace function public.username_available(name text)
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select not exists (select 1 from public.profiles where username = lower(name));
$$;
grant execute on function public.username_available(text) to anon, authenticated;

-- O perfil nasce junto com a conta: se o @ já existir, o cadastro inteiro falha (o @ nunca repete).
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, username, name)
  values (new.id, lower(new.raw_user_meta_data ->> 'username'), coalesce(new.raw_user_meta_data ->> 'name', ''));
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();
