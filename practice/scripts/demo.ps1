param(
    [string]$CatalogUrl='http://localhost:8081',
    [string]$CartUrl='http://localhost:8082',
    [string]$TransactionUrl='http://localhost:8083'
)
$ErrorActionPreference='Stop'
function Post($url,$body,$headers=@{}) {
    Invoke-RestMethod -Method Post -Uri $url -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 10)
}
$customer=[guid]::NewGuid().ToString()
$headers=@{'X-Customer-Id'=$customer}
$store=Post "$CatalogUrl/api/stores" @{name='iStore Bandung';city='Bandung'}
$product=Post "$CatalogUrl/api/products" @{storeId=$store.id;name='MacBook Pro';description='Laptop untuk pengembangan aplikasi';category='Laptop';imageUrl=''}
$variant=Post "$CatalogUrl/api/products/$($product.product.id)/variants" @{sku="MBP-$([guid]::NewGuid())";variantName='16GB / 512GB / Silver';price=36499000;stock=10}
$item=Post "$CartUrl/api/cart/items" @{variantId=$variant.id;quantity=1;note='Mohon kemasan aman'} $headers
$checkoutHeaders=@{'X-Customer-Id'=$customer;'Idempotency-Key'=[guid]::NewGuid().ToString()}
$transaction=Post "$TransactionUrl/api/checkouts" @{itemIds=@($item.id);recipientName='Pelanggan Demo';recipientPhone='081234567890';shippingAddress='Bandung, Jawa Barat';courier='REGULAR'} $checkoutHeaders
Write-Output "Customer ID: $customer"
Write-Output "Transaction ID: $($transaction.id)"
$transaction | ConvertTo-Json -Depth 10
Invoke-RestMethod -Uri "$TransactionUrl/api/transactions" -Headers $headers | ConvertTo-Json -Depth 10
