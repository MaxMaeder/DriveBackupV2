const userCode = document.getElementById('codeInput');
const nextButton = document.getElementById('nextButton');
const spinner = document.getElementById('spinner');
const errorAlert = document.getElementById('errorAlert');

document.getElementById('form-code').addEventListener('submit', function(e){
  e.preventDefault();
  setLoading(true);
  fetch('/provider/' + userCode.value.trim()).then((res) => {
    return res.json();
  }).then((res) => {
    userCode.value = '';
    setLoading(false);
    if (res.success) {
      location.href = res.verifyURL;
    } else {
      displayError(res.msg);
    }
  })
})

function setLoading(value) {
  nextButton.disabled = value;
  spinner.style.display = (value) ? '' : 'none';
}

function displayError(msg) {
  errorAlert.innerHTML = msg;
  errorAlert.style.display = '';
}