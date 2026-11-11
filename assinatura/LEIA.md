# Assinatura do APK

O Android só aceita atualizar um app por cima se o APK novo tiver a mesma
assinatura do instalado. Por isso a chave de produção do Plantão Seguro
precisa ser sempre a mesma, e precisa estar guardada com segurança.

## O que existe aqui

- `chave.tgz.enc`: o arquivo da chave (`release.jks`) e as senhas dela
  (`chave.properties`), **criptografados** com AES-256 (OpenSSL, PBKDF2 com
  600.000 iterações). Sem a senha `PS_CHAVE` o arquivo é inútil.

Nada além disso vai para o repositório: o `.gitignore` bloqueia `*.jks`,
`*.keystore`, `chave.properties` e qualquer outro arquivo desta pasta.

## Como o GitHub assina

1. O dono do repositório cria o secret `PS_CHAVE` em
   *Settings → Secrets and variables → Actions*.
2. O workflow `.github/workflows/android.yml` abre o arquivo com essa senha,
   passa o caminho e as senhas para o Gradle por variáveis de ambiente
   (`PS_KEYSTORE`, `PS_KEYSTORE_PASSWORD`, `PS_KEY_ALIAS`, `PS_KEY_PASSWORD`)
   e mascara as senhas no log.
3. Sem o secret, o APK de release sai com uma assinatura de teste e o
   workflow avisa. Esse APK não deve ser distribuído.

## Certificado de produção

- Titular: `CN=Plantao Seguro, O=Plantao Seguro, C=BR`
- Válido até 2126
- SHA-256: `F7:E8:77:22:7A:C1:5A:26:B8:8E:B9:75:F7:37:BD:BD:AB:4B:E0:66:2D:2E:FE:15:19:18:72:DC:56:63:53:73`

Qualquer APK oficial do Plantão Seguro a partir da 3.0.0 tem esse
certificado. Para conferir: `apksigner verify --print-certs arquivo.apk`.

## Recuperar a chave (por exemplo, para compilar em outro computador)

```bash
export PS_CHAVE='a senha'
openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 -in assinatura/chave.tgz.enc -pass env:PS_CHAVE | tar xz
```

Guarde a senha fora do GitHub também (um gerenciador de senhas, por exemplo).
Se ela for perdida, não há como gerar novas versões que atualizem por cima
das instaladas: todo mundo teria de desinstalar e instalar de novo.

## A versão 1.0.0

O APK v1.0.0 foi assinado com a chave de depuração do computador onde foi
compilado (SHA-256 `E2:73:A8:BC:…:4D:49`). Como essa chave não está disponível,
a 3.0.0 usa a chave de produção acima, e quem tem a 1.0.0 precisa desinstalá-la
uma vez antes de instalar a 3.0.0.
