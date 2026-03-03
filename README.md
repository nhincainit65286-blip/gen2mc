GoogleChat automatically translates all messages sent or received by the client to/from a configured language using Google Translate through LibJF.

Both directions of translation can be configured and GoogleChat can be disabled completely from the config, though server-specific configs are not implemented.

If you want to use GoogleChat serverside (which is supported btw), a config like the following is recommended:
```json
{
  "enabled": true,
  "serverLanguage": "en",
  "clientLanguage": "auto"
}
```

You should also enable previews-chat in your server.properties to allow signed message translations