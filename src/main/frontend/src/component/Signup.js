import React, { useState, useEffect } from 'react';
import {useNavigate} from "react-router-dom";
import '../css/Singup.module.css';
import Layout from '../component/layout/Layout';
import axios from "axios";
import styled from '../css/Singup.module.css'

function Signup(props){

    const navigate = useNavigate();
    const [Email, setEmail] = useState("");
    const [Name, setName] = useState("");
    const [Password, setPassword] = useState("");
    const [ConfirmPassword, setConfirmPassword] = useState("");
    const [phoneNumber, setPhoneNumber] = useState("");
    const [phoneNumberCheck, setPhoneNumberCheck] = useState("");
    const [JSONObject, JSONObjectChange] = useState([]);
    const [bool, setBool] = useState(false);
    const object = new Object();

    useEffect(()=>{

        object.email = Email;
        object.name = Name;
        object.password = Password;
        object.phonenumber = phoneNumber;
        JSONObjectChange(JSON.stringify(object));

    },[Email,Name,Password,phoneNumber])

    const onEmailHandler = (event) => {
        setEmail(event.currentTarget.value);
    }
    const onNameHandler = (event) => {
        setName(event.currentTarget.value);
    }
    const onPasswordHandler = (event) => {
        setPassword(event.currentTarget.value);
    }
    const onConfirmPasswordHandler = (event) => {
        setConfirmPassword(event.currentTarget.value);
    }
    const onPhoneNumberHandler = (event) => {
        setPhoneNumber(event.currentTarget.value);
    }
    const onSubmitHandler = (event) => {
        event.preventDefault();

        if(Password !== ConfirmPassword){
            return alert('비밀번호와 비밀번호 확인이 같지 않습니다.')
        }

    }


    return (
        <Layout>
            <div className={styled.join} style={{
            display: 'flex', justifyContent: 'center', alignItems: 'center',
            width: '100%', height: '100vh' , position : "relative", top : "-220px",
            }}>
            <form style={{ display: 'flex', flexDirection: 'column'}}
                onSubmit={onSubmitHandler}
            >
                <h4>Email</h4>
                <input className={styled.inputBox} type='email' value={Email} placeholder = '입력해주세요' onChange={onEmailHandler}/>
                <h4>Name</h4>
                <input className={styled.inputBox}  type='text' value={Name} placeholder = '입력해주세요' onChange={onNameHandler}/>
                <h4>Password</h4>
                <input className={styled.inputBox}  type='password' value={Password} placeholder = '입력해주세요' onChange={onPasswordHandler}/>
                <h4>Confirm Password</h4>
                <input className={styled.inputBox}  type='password' value={ConfirmPassword} placeholder = '입력해주세요' onChange={onConfirmPasswordHandler}/>
                <h4>phoneNumber</h4>
                <input className={styled.inputBox}  type='text' value={phoneNumber} placeholder = '입력해주세요' onChange={onPhoneNumberHandler}/>
                <span><button onClick={()=>{
                    {
                        if(phoneNumber.length !== 11){
                            alert("전화번호 입력이 잘못되었습니다.");
                        }
                        else{
                            axios.get("/api/auth/check/sendSMS",
                                {params: {to : phoneNumber}},
                                {
                                    withCredentials: true,
                                })
                                .then((response)=>{
                                    setBool(true);
                                    alert("전화번호로 인증키를 보냈습니다. 확인해주세요. 다시 받을려면 '인증번호 받기' 버튼을 다시 눌러주세요.");
                                })
                                .catch((response)=>{ alert("인증~~~~ 실패~~~~ 다시~~~~ 시작~~~~"); })
                        }
                    }
                }}>인증번호 받기</button></span>
                {bool ? <OnPhoneCheck phoneNumber={phoneNumber} phoneNumberCheck={phoneNumberCheck} setPhoneNumberCheck={setPhoneNumberCheck}/> : null}
                <br />
                <button className={styled.joinB}  formAction='' onClick={()=>{
                    axios.post("/api/auth/signup",
                        JSONObject,
                        {
                            withCredentials : true,
                            headers : {"Content-Type": 'application/json'}
                        })
                        .then((response) => {
                            console.log(response.data.data);
                            navigate("/auth/login");
                        })
                        .catch((response) => { console.log('Error!') });
                }}>
                    회원가입
                </button>
            </form>
        </div>
        </Layout>
    )
}

function OnPhoneCheck(props){
    return(
        <>
            <input className={styled.inputBox} type='text' value={props.phoneNumberCheck} placeholder = '입력해주세요' onChange={(e)=> {
                props.setPhoneNumberCheck(e.target.value);
            }}/>
            <span><button onClick={()=>{
                {
                    axios.get("/api/auth/check/verifySMS",
                        {params: {code: props.phoneNumberCheck, to : props.phoneNumber}},
                        {
                            withCredentials: true,
                        })
                        .then((response)=>{ alert("전화번호 인증에 성공하셨습니다."); })
                        .catch((response)=>{ alert("인증~~~~ 실패~~~~ 다시~~~~ 시작~~~~"); })
                }
            }}>인증번호 확인</button></span>
        </>
    )
}

export {Signup};