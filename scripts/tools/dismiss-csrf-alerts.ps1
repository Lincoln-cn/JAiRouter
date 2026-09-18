$alerts = gh api "repos/Lincoln-cn/JAiRouter/code-scanning/alerts?state=open&per_page=100" | ConvertFrom-Json
$csrf = $alerts | Where-Object { $_.rule.id -eq 'java/spring-disabled-csrf-protection' }
foreach ($a in $csrf) {
  $n = $a.number
  $path = $a.most_recent_instance.location.path
  if ($path -like '*TestSecurityConfig*') {
    $reason = 'used in tests'
    $comment = 'Test-only security config for controller tests; not production code.'
  } else {
    $reason = "won't fix"
    $comment = 'Stateless API gateway using Bearer/API Key (NoOpServerSecurityContextRepository, no cookie session). CSRF is not applicable; code comment documents the constraint.'
  }
  Write-Host "Dismissing #$n $path reason=$reason"
  gh api -X PATCH "repos/Lincoln-cn/JAiRouter/code-scanning/alerts/$n" -f state=dismissed -f dismissed_reason="$reason" -f dismissed_comment="$comment" | Out-Null
}
$open = gh api "repos/Lincoln-cn/JAiRouter/code-scanning/alerts?state=open&per_page=100" | ConvertFrom-Json
$openCsrf = @($open | Where-Object { $_.rule.id -eq 'java/spring-disabled-csrf-protection' })
Write-Host "Remaining open CSRF alerts: $($openCsrf.Count)"
