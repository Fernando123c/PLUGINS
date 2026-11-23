-- =====================================================================
--  RbFernan - Panel de licencias del PLUGIN (Supabase / PostgreSQL)
--  Supabase > SQL Editor > New query > pega TODO esto > Run
--  Se puede ejecutar varias veces sin romper nada.
-- =====================================================================

create extension if not exists pgcrypto;

-- ---------------------------- Tablas ----------------------------
create table if not exists public.plugin_licenses (
  id           uuid primary key default gen_random_uuid(),
  key          text not null unique,
  note         text,
  active       boolean not null default true,
  auto_approve boolean not null default false,   -- aprobar solo el primer servidor automaticamente
  max_servers  integer check (max_servers is null or max_servers > 0),  -- null = ilimitado
  expires_at   timestamptz,                       -- null = no vence
  created_at   timestamptz not null default now()
);

create table if not exists public.plugin_servers (
  id             uuid primary key default gen_random_uuid(),
  license_id     uuid not null references public.plugin_licenses(id) on delete cascade,
  hwid           text not null,                   -- identificador del servidor (server-id.txt)
  ip             text,                            -- IP REAL desde la que se conecta (la detecta el panel)
  server_name    text,                            -- nombre que puso el dueno en el config
  port           integer,
  mc_version     text,
  plugin_version text,
  status         text not null default 'pending' check (status in ('pending','approved','rejected')),
  first_seen     timestamptz not null default now(),
  last_seen      timestamptz not null default now(),
  created_at     timestamptz not null default now(),
  unique (license_id, hwid)
);
create index if not exists plugin_servers_license_idx on public.plugin_servers (license_id, last_seen desc);

-- ---------------------------- Seguridad ----------------------------
-- El panel (staff con sesion) ve y edita todo. El plugin NO toca las tablas:
-- solo llama a la funcion plugin_check de abajo.
grant usage on schema public to anon, authenticated;
grant select, insert, update, delete on public.plugin_licenses, public.plugin_servers to authenticated;
revoke all on public.plugin_licenses, public.plugin_servers from anon;

alter table public.plugin_licenses enable row level security;
alter table public.plugin_servers  enable row level security;

drop policy if exists "admins_lic" on public.plugin_licenses;
create policy "admins_lic" on public.plugin_licenses for all to authenticated using (true) with check (true);
drop policy if exists "admins_srv" on public.plugin_servers;
create policy "admins_srv" on public.plugin_servers for all to authenticated using (true) with check (true);

-- ---------------------------- Funcion que llama el plugin ----------------------------
create or replace function public.plugin_check(
  p_key text, p_name text, p_port integer, p_mc text, p_version text, p_hwid text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  lic     public.plugin_licenses;
  srv     public.plugin_servers;
  real_ip text;
  approved_count integer;
  new_status text;
begin
  -- IP REAL del servidor de Minecraft (la primera de x-forwarded-for)
  begin
    real_ip := split_part(coalesce(current_setting('request.headers', true)::json ->> 'x-forwarded-for', ''), ',', 1);
  exception when others then real_ip := null;
  end;
  if real_ip = '' then real_ip := null; end if;

  select * into lic from public.plugin_licenses where key = upper(trim(p_key));
  if not found then
    return jsonb_build_object('ok', false, 'approved', false, 'status', 'invalid', 'msg', 'La licencia no existe.');
  end if;
  if not lic.active then
    return jsonb_build_object('ok', false, 'approved', false, 'status', 'invalid', 'msg', 'La licencia esta desactivada.');
  end if;
  if lic.expires_at is not null and lic.expires_at < now() then
    return jsonb_build_object('ok', false, 'approved', false, 'status', 'invalid', 'msg', 'La licencia esta vencida.');
  end if;

  select * into srv from public.plugin_servers where license_id = lic.id and hwid = coalesce(p_hwid, '');

  if not found then
    select count(*) into approved_count from public.plugin_servers where license_id = lic.id and status = 'approved';
    if lic.auto_approve and (lic.max_servers is null or approved_count < lic.max_servers) then
      new_status := 'approved';
    else
      new_status := 'pending';
    end if;
    insert into public.plugin_servers (license_id, hwid, ip, server_name, port, mc_version, plugin_version, status)
    values (lic.id, coalesce(p_hwid,''), real_ip, left(coalesce(p_name,''),80), p_port, left(coalesce(p_mc,''),40),
            left(coalesce(p_version,''),20), new_status)
    returning * into srv;
  else
    update public.plugin_servers
       set ip = coalesce(real_ip, ip),
           server_name = left(coalesce(p_name, server_name), 80),
           port = coalesce(p_port, port),
           mc_version = left(coalesce(p_mc, mc_version), 40),
           plugin_version = left(coalesce(p_version, plugin_version), 20),
           last_seen = now()
     where id = srv.id
     returning * into srv;
  end if;

  return jsonb_build_object(
    'ok', true,
    'approved', srv.status = 'approved',
    'status', srv.status,
    'msg', case srv.status
             when 'approved' then 'Servidor aprobado.'
             when 'rejected' then 'Este servidor fue rechazado por el staff.'
             else 'Servidor pendiente de aprobacion en el panel.' end);
end;
$$;

revoke all on function public.plugin_check(text, text, integer, text, text, text) from public;
grant execute on function public.plugin_check(text, text, integer, text, text, text) to anon, authenticated;
